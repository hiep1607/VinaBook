package vn.fs.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.ChatActionDto;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatProductDto;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.dto.chat.ChatStatusDto;
import vn.fs.dto.chat.ChatTurnDto;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

@Service
public class ChatService extends ChatLocalDialogue {

	public ChatService(ProductRepository products, AiGateway ai, ChatIntentService intents,
			ChatRetrievalService retrieval, ChatPolicyService policies,
			ChatOrderSupportService orderSupport, ChatAnalyticsService analytics,
			CurrentUserAccess access) {
        super(products,ai,intents,retrieval,policies,orderSupport,analytics,access);
	}

	private final ChatDialogueMemory memory = new ChatDialogueMemory();
	private ChatMemoryStore memoryStore;
	private ChatAccountMemoryService accountMemory;
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setMemoryStore(ChatMemoryStore value) { memoryStore = value; }
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setAccountMemory(ChatAccountMemoryService value) { accountMemory = value; }
    /** Compatibility seam for historical planner fixtures; no production bean is injected here. */
    @Deprecated
    public void setOrchestrator(ChatPlanner value) { orchestrator = value; }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setTools(ChatToolRegistry value) { tools=value; }
    private ChatStructuredPipeline structuredPipeline;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setStructuredPipeline(ChatStructuredPipeline value) { structuredPipeline=value; }
    private CartSyncService syncedCart;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSyncedCart(CartSyncService value){syncedCart=value;}

	public ChatResponseDto reply(ChatRequestDto request) {
		if (request.getRequestId() == null || request.getRequestId().isBlank())
			request.setRequestId(java.util.UUID.randomUUID().toString());
		if((structuredPipeline==null || !ai.isConfigured()) && syncedCart!=null && access.currentEmail()!=null)
            request.setCartItems(syncedCart.chatItems());
		Long userId = access.currentUserId();
		String owner = userId == null ? null : userId.toString();
		ChatDialogueMemory.State state = memory.get(owner, request.getSessionId());
		synchronized (state) {
			state.recommendationIntakeRequired = true;
			if (memoryStore != null && !state.loaded) memoryStore.restore(userId, request.getSessionId(), state);
			if (request.getRequestId() != null && request.getRequestId().equals(state.lastRequest) && state.lastResponse != null)
				return state.lastResponse;
			if (memoryStore != null) {
				ChatResponseDto duplicate = memoryStore.duplicate(userId, request.getSessionId(), request.getRequestId());
				if (duplicate != null) return duplicate;
				state.recentDialogue = memoryStore.relevantTurns(userId, request.getSessionId(), request.getMessage());
				if (!state.recentDialogue.isEmpty()) {
					List<ChatTurnDto> recent = new ArrayList<>();
					for (java.util.Map<String, String> turn : state.recentDialogue) {
						recent.add(new ChatTurnDto("user", turn.get("user")));
						recent.add(new ChatTurnDto("assistant", turn.get("assistant")));
					}
					request.setHistory(recent.subList(Math.max(0, recent.size() - 10), recent.size()));
				}
			}
			if (accountMemory != null && userId != null) state.accountMemory = accountMemory.context(userId);
			ChatDialogueMemory.State liveState = state;
            state = ChatDialogueMemory.copy(liveState);
			ChatResponseDto response;
			if (guardedAnswer(request.getMessage()) != null || privateText(request.getMessage())) {
				response = dialogue(request, state, false);
			} else if((structuredPipeline==null || !ai.isConfigured()) && customerFeatureQuestion(intents.normalize(request.getMessage()))) {
                response=customerFeatureHelp(request,state);
            } else {
				if (structuredPipeline != null && ai.isConfigured()) {
					response = structuredPipeline.reply(request, state);
				} else {
				List<Product> catalog = products.findByStatusTrue().stream()
					.filter(p -> Boolean.TRUE.equals(p.getStatus())).collect(Collectors.toList());
				ChatRecommendationIntake intake = new ChatRecommendationIntake(intents, retrieval);
				ChatRecommendationIntake.Decision decision = intake.prepare(request, state, catalog);
				if (!decision.question().isBlank()) {
					response = intakeClarification(request, decision.question());
				} else {
					ChatRequestDto effective = request;
					if (!request.getMessage().equals(decision.message())) {
						effective = new ChatRequestDto();
						effective.setMessage(decision.message());
						effective.setSessionId(request.getSessionId());
						effective.setRequestId(request.getRequestId());
						effective.setHistory(request.getHistory());
						effective.setContextProductIds(request.getContextProductIds());
						effective.setActiveProductId(request.getActiveProductId());
						effective.setCartItems(request.getCartItems());
                        effective.setWishlistProductIds(request.getWishlistProductIds());
					}
					response = dialogue(effective, state, false);
					// A planner/classifier miss must not leak a recommendation before intake is complete.
					if (response.getIntent() == ChatIntent.DISCOVER_BOOKS && !response.getProducts().isEmpty()
						&& !ChatRecommendationIntake.ready(state) && !intake.exempt(request.getMessage(), catalog)) {
						state.recommendationPending = true;
						response = intakeClarification(request, ChatRecommendationIntake.question(state));
					}
				}
			}
				}
			state.lastRequest = response.getIntent() == ChatIntent.ORDER_SUPPORT ? null : request.getRequestId();
			state.lastResponse = response.getIntent() == ChatIntent.ORDER_SUPPORT ? null : response;
            synchronized(liveState.commitLock) {
                if(liveState.cleared || Thread.currentThread().isInterrupted())
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Cuộc trò chuyện đã đóng.");
                if (memoryStore != null) memoryStore.save(userId, request.getSessionId(), request.getRequestId(),
                    request.getMessage(), response, state,
                    !privateText(request.getMessage()) && response.getIntent() != ChatIntent.ORDER_SUPPORT);
                ChatDialogueMemory.commit(liveState,state);
            }
			if (accountMemory != null) accountMemory.observeChat(userId, request.getMessage(), response.getIntent());
			return response;
		}
	}

    private boolean customerFeatureQuestion(String q) {
        return q.matches("(?s).*(?:ma giam gia|voucher|ma uu dai|thong bao (?:co hang|cua toi)|bao (?:minh|toi|cho toi) khi (?:co|sach ve) hang|kiem tra hoan tien|trang thai hoan tien|tinh trang hoan tien).*" );
    }
    private ChatResponseDto customerFeatureHelp(ChatRequestDto request,ChatDialogueMemory.State state) {
        String q=intents.normalize(request.getMessage());long started=System.currentTimeMillis();
        if(q.matches("(?s).*(?:ma giam gia|voucher|ma uu dai).*")) {
            var response=finish(request,"Bạn có mã ưu đãi thì nhập ở ô Mã giảm giá trong bước thanh toán trên web hoặc trong chat nhé. Mình cần kiểm tra sách trong đơn, thời hạn và lượt dùng để báo đúng số tiền được giảm; bạn sẽ thấy tổng tiền trước khi xác nhận đặt hàng.",ChatIntent.PURCHASE,List.of(),false,false,state,started);
            response.getActions().clear();response.getActions().add(new ChatActionDto("VIEW_CART","Mở giỏ hàng",null,"/cart",false));
            if(request.getCartItems()!=null&&!request.getCartItems().isEmpty())response.getActions().add(new ChatActionDto("CHECKOUT_CART","Đặt hàng và áp dụng mã",null,null,false));
            return response;
        }
        if(q.contains("hoan tien")) {
            var matcher=java.util.regex.Pattern.compile("(?:don|#)\\s*#?\\s*([0-9]{1,15})").matcher(q);Long id=matcher.find()?Long.valueOf(matcher.group(1)):state.focusedOrderId;
            String answer=id==null?"Mình giúp bạn kiểm tra được nhé. Bạn gửi mã đơn để mình xem trạng thái hoàn tiền đúng đơn trong tài khoản của bạn; việc hoàn tiền do nhân viên xác nhận sau khi đối soát.":orderSupport.orderById(id);
            var response=finish(request,answer,ChatIntent.ORDER_SUPPORT,List.of(),false,false,state,started);response.getActions().clear();
            if(id!=null&&answer.startsWith("Đơn #"))response.getActions().add(new ChatActionDto("VIEW_ORDER","Xem trạng thái hoàn tiền",null,"/orders/"+id,false));return response;
        }
        List<Product> selected=new ArrayList<>();Long id=request.getActiveProductId()!=null?request.getActiveProductId():state.focus;
        if(id!=null)products.findById(id).filter(p->Boolean.TRUE.equals(p.getStatus())).ifPresent(selected::add);
        String answer=selected.isEmpty()?"Mình có thể báo khi cuốn bạn đang chờ có hàng trở lại. Bạn mở đúng cuốn sách rồi chọn Báo mình khi có hàng; cần đăng nhập để mình lưu thông báo vào tài khoản của bạn nhé.":selected.get(0).getQuantity()>0?"Cuốn này đang có hàng theo catalog. Bạn có thể mở trang sách để kiểm tra và đặt mua nhé.":"Mình hiểu bạn đang chờ cuốn này. Bạn chọn Báo mình khi có hàng trên thẻ sách bên dưới nhé; thông báo sẽ nằm trong tài khoản khi sách về, và bạn có thể bỏ theo dõi bất cứ lúc nào.";
        var response=finish(request,answer,ChatIntent.PRODUCT_INFO,selected,false,false,state,started);response.getActions().add(new ChatActionDto("VIEW_NOTIFICATIONS","Xem thông báo của tôi",null,"/notifications",false));return response;
    }
	public List<java.util.Map<String, Object>> history(String sessionId) {
		return memoryStore == null ? List.of() : memoryStore.history(access.currentUserId(), sessionId);
	}

    public vn.fs.dto.chat.ChatHistoryPage historyPage(String sessionId,Long before,int limit) {
        return memoryStore==null?new vn.fs.dto.chat.ChatHistoryPage(List.of(),null,false):memoryStore.historyPage(access.currentUserId(),sessionId,before,limit);
    }
	public void clearConversation(String sessionId) {
		Long userId = access.currentUserId();
        String owner=userId==null?null:userId.toString();
        ChatDialogueMemory.State state=memory.get(owner,sessionId);
        synchronized(state.commitLock) {
            if (memoryStore != null) memoryStore.clear(userId, sessionId);
            state.cleared=true;
            memory.clear(owner,sessionId);
        }
	}

	public void observeProductView(Long productId) {
		if (accountMemory != null) accountMemory.observeProductView(access.currentUserId(), productId);
	}

	public java.util.Map<String, Object> accountContext() {
		return accountMemory == null ? java.util.Map.of() : accountMemory.context(access.currentUserId());
	}

}

package vn.fs.automation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.MailInfo;
import vn.fs.entity.User;
import vn.fs.service.MailOutboxStore;
import static vn.fs.automation.AutomationDataSource.*;

@Service
public class AutomationService {
    public static final Set<String> TYPES=Set.of("RESTOCK","PRICE_DROP","CART_PRICE_CHANGE","ORDER_STATUS","CART_REMINDER","REVIEW_REMINDER","AUTHOR_RELEASE");
    private final CurrentUserAccess access;
    private final AutomationNoticeRepository notices;
    private final AutomationPreferenceRepository preferences;
    private final AutomationRuleRepository rules;
    private final AutomationStateRepository states;
    private final AutomationRunRepository runs;
    private final AutomationDataSource data;
    private final MailOutboxStore outbox;
    private final ObjectMapper json;
    private final boolean mailEnabled;
    private final String mailUsername;
    private final String mailPassword;
    private final String frontend;
    private Clock clock=Clock.systemUTC();

    public AutomationService(CurrentUserAccess access,AutomationNoticeRepository notices,AutomationPreferenceRepository preferences,
            AutomationRuleRepository rules,AutomationStateRepository states,AutomationRunRepository runs,AutomationDataSource data,
            MailOutboxStore outbox,ObjectMapper json,@Value("${automation.email-enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.username:}") String mailUsername,@Value("${spring.mail.password:}") String mailPassword,
            @Value("${payment.frontend-url:http://localhost:4200}") String frontend){
        this.access=access;this.notices=notices;this.preferences=preferences;this.rules=rules;this.states=states;this.runs=runs;
        this.data=data;this.outbox=outbox;this.json=json;this.mailEnabled=mailEnabled;this.mailUsername=mailUsername;this.mailPassword=mailPassword;
        this.frontend=frontend.replaceAll("/+$","");
    }
    void clock(Clock clock){this.clock=clock;}
    Instant now(){return clock.instant();}
    static ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    private User owner(boolean lock){
        Long id=access.currentUserId();if(id==null)throw fail(HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để xem thông báo nhé.");
        User user=data.owner(id,lock);if(user==null||!Boolean.TRUE.equals(user.getStatus()))throw fail(HttpStatus.UNAUTHORIZED,"Phiên đăng nhập không còn khả dụng.");return user;
    }
    public void requireAdmin(){if(!access.isAdmin())throw fail(HttpStatus.FORBIDDEN,"Chỉ quản trị viên được quản lý tự động hóa.");}
    boolean emailAvailable(){return mailEnabled&&!mailUsername.isBlank()&&!mailPassword.isBlank()&&(frontend.startsWith("https://")||frontend.startsWith("http://localhost:")||frontend.startsWith("http://127.0.0.1:"));}

    @Transactional public void initialize(){
        for(var rule:defaultRules()){
            var existing=rules.findById(rule.getKey());
            if(existing.isEmpty())rules.save(rule);
            else if("PRICE_DROP".equals(rule.getKey())&&(!rule.getLabel().equals(existing.get().getLabel())||!rule.getDescription().equals(existing.get().getDescription()))){var stored=existing.get();stored.setLabel(rule.getLabel());stored.setDescription(rule.getDescription());rules.save(stored);}
        }
        if(!states.existsById("runner")){var row=new AutomationState();row.setStateKey("runner");row.setValueJson("{\"cursor\":0}");row.setUpdatedAt(now());states.save(row);}
    }
    static List<AutomationRule> defaultRules(){
        return List.of(rule("RESTOCK","Báo sách có hàng","Thông báo khi cuốn sách đã đăng ký được nhập lại.",null,null),
            rule("PRICE_DROP","Giảm giá sách yêu thích","Báo khi giá thực bán của sách đang yêu thích giảm.",null,null),
            rule("CART_PRICE_CHANGE","Giá sách trong giỏ","Báo tăng hoặc giảm giá sách đang trong giỏ khi khách đã bật nhận loại tin này.",null,null),
            rule("ORDER_STATUS","Tiến trình đơn hàng","Cập nhật khi đơn được tạo hoặc chuyển trạng thái.",null,null),
            rule("CART_REMINDER","Nhắc giỏ hàng","Nhắc một lần cho mỗi lần giỏ thay đổi và chưa có đơn mua mới.",24,null),
            rule("REVIEW_REMINDER","Mời đánh giá sau mua","Mời đánh giá sau khi hệ thống ghi nhận đơn đã giao; bỏ qua sách đã đánh giá và đơn có yêu cầu đổi trả.",168,null),
            rule("AUTHOR_RELEASE","Sách mới của tác giả","Báo sách mới được thêm vào VinaBook của tác giả đang theo dõi.",null,null),
            rule("LOW_STOCK","Cảnh báo tồn kho","Hiển thị sách còn ít hàng và số lượt đăng ký chờ trong quản trị.",null,5));
    }
    private static AutomationRule rule(String key,String label,String description,Integer delay,Integer threshold){var r=new AutomationRule();r.setKey(key);r.setLabel(label);r.setDescription(description);r.setDelayHours(delay);r.setThreshold(threshold);return r;}
    private Map<String,AutomationRule> ruleMap(){return rules.findAll().stream().collect(Collectors.toMap(AutomationRule::getKey,r->r));}
    private boolean enabled(Map<String,AutomationRule> config,String key){return config.containsKey(key)&&config.get(key).isEnabled();}
    private int delay(Map<String,AutomationRule> config,String key,int fallback){var r=config.get(key);return r==null||r.getDelayHours()==null?fallback:r.getDelayHours();}

    @Transactional public Map<String,Object> inbox(int page,int size,String type,boolean unread){
        User user=owner(false);String selected=type==null?"ALL":type.toUpperCase(Locale.ROOT);
        if(!"ALL".equals(selected)&&!TYPES.contains(selected))throw fail(HttpStatus.BAD_REQUEST,"Loại thông báo chưa hợp lệ.");
        if(page<0||page>10000||size<1||size>100)throw fail(HttpStatus.BAD_REQUEST,"Phân trang chưa hợp lệ.");
        var result=notices.inbox(user.getUserId(),selected,unread,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        return Map.of("items",result.getContent().stream().map(this::noticeDto).collect(Collectors.toList()),"total",result.getTotalElements(),"unreadCount",notices.countByOwnerIdAndSeenFalseAndDeletedFalse(user.getUserId()));
    }
    @Transactional(readOnly=true) public Map<String,Long> summary(){return Map.of("unreadCount",notices.countByOwnerIdAndSeenFalseAndDeletedFalse(owner(false).getUserId()));}
    private Map<String,Object> noticeDto(AutomationNotice n){
        var dto=new LinkedHashMap<String,Object>();dto.put("id",n.getId());dto.put("type",n.getType());dto.put("title",n.getTitle());dto.put("message",n.getMessage());dto.put("link",n.getLink());dto.put("productId",n.getProductId());dto.put("orderId",n.getOrderId());dto.put("seen",n.isSeen());dto.put("createdAt",n.getCreatedAt());return dto;
    }
    @Transactional public void seen(String id){User u=owner(true);var n=ownedNotice(u,id);n.setSeen(true);data.syncRestockRead(u.getUserId(),n);notices.save(n);}
    @Transactional public void seenAll(){User u=owner(true);notices.markAllSeen(u.getUserId());data.syncAllRestockRead(u.getUserId());}
    @Transactional public void remove(String id){User u=owner(true);var n=ownedNotice(u,id);n.setDeleted(true);n.setSeen(true);data.syncRestockRead(u.getUserId(),n);notices.save(n);}
    private AutomationNotice ownedNotice(User u,String id){return notices.findByIdAndOwnerIdAndDeletedFalse(id,u.getUserId()).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Thông báo không còn tồn tại."));}

    @Data public static class PreferenceInput {
        private Boolean restock,priceDrops,cartPriceChanges,orders,cartReminders,reviewReminders,authorReleases,emailEnabled;
        private String emailAddress;
        private List<String> followedAuthors;
    }
    @Transactional public Map<String,Object> getPreferences(){User u=owner(true);return preferenceDto(preference(u.getUserId()),u.getEmail());}
    @Transactional public Map<String,Object> savePreferences(PreferenceInput input){
        User u=owner(true);if(input==null||input.getFollowedAuthors()!=null&&input.getFollowedAuthors().size()>50)throw fail(HttpStatus.BAD_REQUEST,"Bạn có thể theo dõi tối đa 50 tác giả.");
        var p=preference(u.getUserId());
        String destination=input.getEmailAddress()==null?recipient(p,u.getEmail()):normalizeEmail(input.getEmailAddress().isBlank()?u.getEmail():input.getEmailAddress());
        boolean valid=validEmail(destination);
        if((input.getEmailEnabled()==null?p.isEmailEnabled():input.getEmailEnabled())&&!valid)throw fail(HttpStatus.BAD_REQUEST,"Bạn nhập một địa chỉ email nhận thông báo hợp lệ nhé.");
        // Omitted fields preserve older clients; invalid drafts must never prevent opting out.
        if(input.getEmailAddress()!=null&&valid)p.setEmailAddress(destination.equalsIgnoreCase(u.getEmail())?null:destination);
        var available=data.catalog().stream().map(Book::author).filter(a->!a.isBlank()).collect(Collectors.toMap(AutomationService::canonical,a->a,(a,b)->a));
        var followed=new LinkedHashSet<String>();
        for(String name:input.getFollowedAuthors()==null?authors(p):input.getFollowedAuthors()){
            if(name==null||name.trim().isEmpty()||name.length()>200||!available.containsKey(canonical(name)))throw fail(HttpStatus.BAD_REQUEST,"Bạn chọn tác giả đang có sách tại VinaBook nhé.");followed.add(available.get(canonical(name)));
        }
        var state=readState(u.getUserId());
        // Opting in starts at today's price, including changes not yet seen by the scanner.
        if(Boolean.TRUE.equals(input.getCartPriceChanges())&&!p.isCartPriceChanges()||Boolean.TRUE.equals(input.getPriceDrops())&&!p.isPriceDrops()){
            var snapshot=data.snapshot(u.getUserId(),List.of());
            if(Boolean.TRUE.equals(input.getCartPriceChanges())&&!p.isCartPriceChanges()){
                state.cartPrices.clear();snapshot.cart().books().forEach(b->state.cartPrices.put(b.id(),b.price()));
            }
            if(Boolean.TRUE.equals(input.getPriceDrops())&&!p.isPriceDrops())for(var favorite:snapshot.favorites()){
                var price=state.prices.computeIfAbsent(favorite.id(),id->new PriceState());price.price=favorite.book().price();
            }
        }
        // A follow begins with the catalog currently present, never an alert for an old book.
        var catalog=data.catalog();var oldAuthors=authors(p);
        for(String author:followed)if(oldAuthors.stream().noneMatch(a->canonical(a).equals(canonical(author))))state.authorBooks.put(canonical(author),catalog.stream().filter(b->canonical(b.author()).equals(canonical(author))).map(Book::id).collect(Collectors.toSet()));
        state.authorBooks.keySet().retainAll(followed.stream().map(AutomationService::canonical).collect(Collectors.toSet()));
        if(input.getRestock()!=null)p.setRestock(input.getRestock());
        if(input.getPriceDrops()!=null)p.setPriceDrops(input.getPriceDrops());
        if(input.getCartPriceChanges()!=null)p.setCartPriceChanges(input.getCartPriceChanges());
        if(input.getOrders()!=null)p.setOrders(input.getOrders());
        if(input.getCartReminders()!=null)p.setCartReminders(input.getCartReminders());
        if(input.getReviewReminders()!=null)p.setReviewReminders(input.getReviewReminders());
        if(input.getAuthorReleases()!=null)p.setAuthorReleases(input.getAuthorReleases());
        if(input.getEmailEnabled()!=null)p.setEmailEnabled(input.getEmailEnabled());
        p.setFollowedAuthors(encode(followed));
        preferences.save(p);writeState(u.getUserId(),state);return preferenceDto(p,u.getEmail());
    }
    @Transactional(readOnly=true) public List<String> availableAuthors(){owner(false);return data.catalog().stream().map(Book::author).filter(a->!a.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());}
    /** Persist the first observed price atomically with the customer's cart edit. */
    @Transactional(propagation=Propagation.MANDATORY) public void trackCart(List<vn.fs.entity.Product> books){
        User user=owner(true);var state=readState(user.getUserId());var ids=new HashSet<Long>();
        for(var product:books){var book=data.book(product);ids.add(book.id());state.cartPrices.putIfAbsent(book.id(),book.price());}
        state.cartPrices.keySet().retainAll(ids);writeState(user.getUserId(),state);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void trackFavorite(vn.fs.entity.Favorite favorite){
        User user=owner(true);if(!user.getUserId().equals(favorite.getUser().getUserId()))throw fail(HttpStatus.FORBIDDEN,"Không thể theo dõi sách của tài khoản khác.");
        var state=readState(user.getUserId());var price=new PriceState();price.price=data.book(favorite.getProduct()).price();state.prices.putIfAbsent(favorite.getFavoriteId(),price);writeState(user.getUserId(),state);
    }
    private AutomationPreference preference(Long id){return preferences.findById(id).orElseGet(()->{var p=new AutomationPreference();p.setOwnerId(id);p.setCreatedAt(now());return preferences.save(p);});}
    List<String> authors(AutomationPreference p){try{return Arrays.asList(json.readValue(p.getFollowedAuthors(),String[].class));}catch(Exception invalid){throw new IllegalStateException("Invalid automation preferences",invalid);}}
    private Map<String,Object> preferenceDto(AutomationPreference p,String accountEmail){var result=new LinkedHashMap<String,Object>(Map.of("restock",p.isRestock(),"priceDrops",p.isPriceDrops(),"orders",p.isOrders(),"cartReminders",p.isCartReminders(),"reviewReminders",p.isReviewReminders(),"authorReleases",p.isAuthorReleases(),"emailEnabled",p.isEmailEnabled(),"followedAuthors",authors(p),"emailAvailable",emailAvailable(),"emailAddress",recipient(p,accountEmail)));result.put("cartPriceChanges",p.isCartPriceChanges());return result;}

    static String normalizeEmail(String address){return address==null?"":address.trim().toLowerCase(Locale.ROOT);}
    static String recipient(AutomationPreference p,String accountEmail){return normalizeEmail(p.getEmailAddress()==null||p.getEmailAddress().isBlank()?accountEmail:p.getEmailAddress());}
    static boolean validEmail(String value){
        if(value==null||value.isBlank()||value.length()>254||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.matches(".*\\s.*"))return false;
        try{var address=new javax.mail.internet.InternetAddress(value,true);address.validate();return !address.isGroup()&&address.getPersonal()==null&&value.equals(address.getAddress())&&value.substring(value.lastIndexOf('@')+1).contains(".");}
        catch(javax.mail.internet.AddressException invalid){return false;}
    }

    @Transactional public int scan(boolean allowEmail){
        data.lockGlobal();var runner=states.findById("runner").orElseThrow();long cursor=0;
        try{cursor=json.readTree(runner.getValueJson()).path("cursor").asLong();}catch(Exception invalid){throw new IllegalStateException("Invalid automation runner checkpoint",invalid);}
        var owners=data.ownersAfter(cursor,25);if(owners.isEmpty())owners=data.ownersAfter(0,25);
        var config=ruleMap();var catalog=data.catalog();int created=0;
        for(Long id:owners){User user=data.owner(id,true);if(user==null||!Boolean.TRUE.equals(user.getStatus()))continue;created+=scanOwner(user,data.snapshot(id,catalog),config,allowEmail);cursor=id;}
        runner.setValueJson(encode(Map.of("cursor",owners.isEmpty()?0:cursor)));runner.setUpdatedAt(now());states.save(runner);return created;
    }

    int scanOwner(User user,Snapshot snapshot,Map<String,AutomationRule> config,boolean allowEmail){
        Long owner=user.getUserId();var pref=preference(owner);var state=readState(owner);int created=0;
        var catalog=snapshot.catalog().stream().collect(Collectors.toMap(Book::id,b->b));snapshot.referencedBooks().forEach(b->catalog.put(b.id(),b));
        for(var watch:snapshot.watches()){
            Book book=catalog.get(watch.productId());
            if(!"AVAILABLE".equals(watch.state())||watch.availableAt()==null)continue;
            String version=watch.availableAt().toString();if(version.equals(state.restockEvents.get(watch.id())))continue;
            if(sellable(book)&&enabled(config,"RESTOCK")&&pref.isRestock())created+=emit(user,pref,"RESTOCK","restock:"+watch.id()+":"+watch.availableAt(),"Sách bạn chờ đã có hàng",book.name()+" đã có hàng trở lại. Tồn kho có thể thay đổi trước khi bạn đặt mua.","/product/"+book.id(),book.id(),null,version,watch.seen(),allowEmail);
            // Disabled or already sold-out events are consumed, never backfilled on re-enable.
            state.restockEvents.put(watch.id(),version);
        }
        // Cart changes include both directions. A single event takes priority over a wishlist drop.
        var cartIds=new HashSet<Long>();var cartNotified=new HashSet<Long>();
        for(Book book:snapshot.cart().books()){
            cartIds.add(book.id());Long previous=state.cartPrices.get(book.id());
            if(previous!=null&&previous.longValue()!=book.price()&&sellable(book)&&enabled(config,"CART_PRICE_CHANGE")&&pref.isCartPriceChanges()){
                state.cartPriceSequence++;
                created+=emit(user,pref,"CART_PRICE_CHANGE","cart-price:"+book.id()+":"+state.cartPriceSequence,"Giá sách trong giỏ vừa thay đổi",book.name()+(book.price()<previous?" giảm từ ":" tăng từ ")+money(previous)+(book.price()<previous?" xuống ":" lên ")+money(book.price())+". Bạn kiểm tra lại giỏ trước khi đặt nhé.","/cart",book.id(),null,previous+":"+book.price(),false,allowEmail);
                cartNotified.add(book.id());
            }
            state.cartPrices.put(book.id(),book.price());
        }
        state.cartPrices.keySet().retainAll(cartIds);
        var favoriteIds=new HashSet<Long>();
        for(var favorite:snapshot.favorites()){
            Book book=favorite.book();favoriteIds.add(favorite.id());PriceState previous=state.prices.get(favorite.id());
            if(previous!=null&&book.price()<previous.price&&sellable(book)&&enabled(config,"PRICE_DROP")&&pref.isPriceDrops()&&!cartNotified.contains(book.id())){
                previous.sequence++;
                created+=emit(user,pref,"PRICE_DROP","price:"+favorite.id()+":"+previous.sequence,"Sách yêu thích vừa giảm giá",book.name()+" giảm từ "+money(previous.price)+" còn "+money(book.price())+".","/product/"+book.id(),book.id(),null,previous.price+":"+book.price(),false,allowEmail);
            }
            if(previous==null)previous=new PriceState();previous.price=book.price();state.prices.put(favorite.id(),previous);
        }
        state.prices.keySet().retainAll(favoriteIds);
        Set<Long> orderIds=new HashSet<>();
        for(var order:snapshot.orders()){
            orderIds.add(order.id());OrderState previous=state.orders.get(order.id());
            boolean changed=previous!=null&&previous.status!=order.status();boolean newOrder=previous==null&&state.initialized&&order.placedAt().compareTo(pref.getCreatedAt())>=0;
            if(previous==null){previous=new OrderState();previous.status=order.status();if(newOrder&&order.status()==2)previous.deliveredAt=now().toEpochMilli();}
            if(changed){previous.sequence++;previous.status=order.status();previous.deliveredAt=order.status()==2?now().toEpochMilli():0;}
            if((changed||newOrder)&&enabled(config,"ORDER_STATUS")&&pref.isOrders())created+=orderNotice(user,pref,order,previous.sequence,allowEmail);
            if(order.status()==2&&previous.deliveredAt>0&&!previous.reviewHandled&&now().toEpochMilli()-previous.deliveredAt>=Duration.ofHours(delay(config,"REVIEW_REMINDER",168)).toMillis()){
                var product=order.productIds().stream().filter(id->!order.ratedProducts().contains(id)).filter(id->catalog.containsKey(id)).findFirst();
                if(product.isPresent()&&!order.returned()&&enabled(config,"REVIEW_REMINDER")&&pref.isReviewReminders())created+=emit(user,pref,"REVIEW_REMINDER","review:"+order.id(),"Bạn đã đọc sách mới chưa?","Chia sẻ cảm nhận về sách trong đơn #"+order.id()+" để giúp những bạn đọc khác lựa chọn nhé.","/product/"+product.get(),product.get(),order.id(),null,false,allowEmail);
                previous.reviewHandled=true;
            }
            state.orders.put(order.id(),previous);
        }
        state.orders.keySet().retainAll(orderIds);
        String fingerprint=hash(snapshot.cart().fingerprint());
        if(!fingerprint.equals(state.cartFingerprint)){state.cartFingerprint=fingerprint;state.cartSince=now().toEpochMilli();state.cartGeneration++;state.cartHandled=false;state.cartSuppressed=false;}
        if(snapshot.orders().stream().anyMatch(o->o.status()!=3&&o.placedAt().toEpochMilli()>=state.cartSince))state.cartSuppressed=true;
        if(!snapshot.cart().books().isEmpty()&&!state.cartHandled&&!state.cartSuppressed&&now().toEpochMilli()-state.cartSince>=Duration.ofHours(delay(config,"CART_REMINDER",24)).toMillis()){
            if(enabled(config,"CART_REMINDER")&&pref.isCartReminders())created+=emit(user,pref,"CART_REMINDER","cart:"+state.cartGeneration,"Giỏ sách vẫn đang chờ bạn","Bạn còn "+snapshot.cart().books().size()+" đầu sách có thể đặt mua trong giỏ. Giá và tồn kho sẽ được kiểm tra lại khi thanh toán.","/cart",null,null,fingerprint,false,allowEmail);
            state.cartHandled=true;
        }
        for(String author:authors(pref)){
            String key=canonical(author);Set<Long> known=state.authorBooks.get(key);Set<Long> current=snapshot.catalog().stream().filter(b->canonical(b.author()).equals(key)).map(Book::id).collect(Collectors.toSet());
            if(known!=null)for(Book book:snapshot.catalog())if(canonical(book.author()).equals(key)&&!known.contains(book.id())&&sellable(book)&&enabled(config,"AUTHOR_RELEASE")&&pref.isAuthorReleases())created+=emit(user,pref,"AUTHOR_RELEASE","author:"+hash(key)+":"+book.id(),"VinaBook vừa có thêm sách của "+author,book.name()+" vừa được thêm vào cửa hàng. Khám phá cuốn sách mới của tác giả bạn theo dõi nhé.","/product/"+book.id(),book.id(),null,hash(key),false,allowEmail);
            if(known==null)known=new HashSet<>();known.addAll(current);state.authorBooks.put(key,known);
        }
        state.authorBooks.keySet().retainAll(authors(pref).stream().map(AutomationService::canonical).collect(Collectors.toSet()));
        state.initialized=true;writeState(owner,state);return created;
    }
    private int orderNotice(User user,AutomationPreference pref,OrderRow order,int sequence,boolean email){
        String label=order.status()==0?"đã được tiếp nhận":order.status()==1?"đang được giao":order.status()==2?"đã giao thành công":order.status()==3?"đã được hủy":"đã cập nhật trạng thái";
        return emit(user,pref,"ORDER_STATUS","order:"+order.id()+":"+sequence+":"+order.status(),"Cập nhật đơn #"+order.id(),"Đơn hàng #"+order.id()+" "+label+". Mở đơn để xem chi tiết.","/orders/"+order.id(),null,order.id(),String.valueOf(order.status()),false,email);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void orderChanged(Long id){
        var order=data.findOrder(id);if(order==null||order.getUser()==null)return;var user=data.owner(order.getUser().getUserId(),true);if(user==null||!Boolean.TRUE.equals(user.getStatus()))return;
        var pref=preference(user.getUserId());var state=readState(user.getUserId());var current=data.order(order);var previous=state.orders.get(id);
        if(previous!=null&&previous.status==current.status())return;
        if(previous==null)previous=new OrderState();else previous.sequence++;
        previous.status=current.status();previous.deliveredAt=current.status()==2?now().toEpochMilli():0;state.orders.put(id,previous);
        var config=ruleMap();if(enabled(config,"ORDER_STATUS")&&pref.isOrders())orderNotice(user,pref,current,previous.sequence,true);writeState(user.getUserId(),state);
    }

    private int emit(User user,AutomationPreference pref,String type,String event,String title,String message,String link,Long product,Long order,String reference,boolean seen,boolean allowEmail){
        if(notices.existsByOwnerIdAndEventKey(user.getUserId(),event))return 0;
        var n=new AutomationNotice();n.setId(UUID.randomUUID().toString());n.setOwnerId(user.getUserId());n.setType(type);n.setEventKey(event);n.setTitle(shorten(title,160));n.setMessage(shorten(message,1000));n.setLink(link);n.setProductId(product);n.setOrderId(order);n.setCreatedAt(now());n.setSourceReference(reference);n.setSeen(seen);
        // Manual checks always stay inside the app, even if mail is enabled for the account.
        String destination=recipient(pref,user.getEmail());
        if(allowEmail&&pref.isEmailEnabled()&&emailAvailable()&&!seen&&validEmail(destination)){
            String body="<p>"+HtmlUtils.htmlEscape(n.getMessage())+"</p><p><a href=\""+HtmlUtils.htmlEscape(frontend+link)+"\">Xem tại VinaBook</a></p><p>Bạn có thể tắt email trong mục Thông báo của tài khoản.</p>";
            try{var mail=new MailInfo(destination,n.getTitle(),body);mail.setFrom(mailUsername);n.setMailJobId(outbox.enqueueJob(mail));n.setEmailRecipientHash(hash(destination));n.setEmailState("QUEUED");}
            catch(RuntimeException unavailable){n.setEmailState("QUEUE_FAILED");}
        }
        notices.save(n);return 1;
    }
    static boolean sellable(Book b){return b!=null&&b.active()&&b.stock()>0&&b.price()>0;}
    static String canonical(String text){return text==null?"":text.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String shorten(String text,int max){return text.length()>max?text.substring(0,max):text;}
    private static String money(long value){return String.format(Locale.forLanguageTag("vi-VN"),"%,d",value)+"đ";}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw new IllegalStateException("Cannot encode automation state",invalid);}}
    AccountState readState(Long owner){return states.findById("owner:"+owner).map(row->{try{return json.readValue(row.getValueJson(),AccountState.class);}catch(Exception invalid){throw new IllegalStateException("Invalid automation checkpoint",invalid);}}).orElseGet(AccountState::new);}
    private void writeState(Long owner,AccountState state){var row=states.findById("owner:"+owner).orElseGet(AutomationState::new);row.setStateKey("owner:"+owner);row.setValueJson(encode(state));row.setUpdatedAt(now());states.save(row);}
    public static class PriceState {public long price;public int sequence;}
    public static class OrderState {public int status;public int sequence;public long deliveredAt;public boolean reviewHandled;}
    public static class AccountState {
        public Map<Long,Long> cartPrices=new HashMap<>();public long cartPriceSequence;
        public boolean initialized;public Map<Long,PriceState> prices=new HashMap<>();public Map<Long,OrderState> orders=new HashMap<>();public Map<String,Set<Long>> authorBooks=new HashMap<>();public Map<Long,String> restockEvents=new HashMap<>();
        public String cartFingerprint="";public long cartSince;public int cartGeneration;public boolean cartHandled;public boolean cartSuppressed;
    }

    @Transactional public Map<String,Object> admin(){requireAdmin();return adminResult();}
    private Map<String,Object> adminResult(){
        var ruleRows=rules.findAll();var stock=ruleRows.stream().filter(r->r.getKey().equals("LOW_STOCK")).findFirst().orElseGet(()->rule("LOW_STOCK","","",null,5));int threshold=stock.getThreshold()==null?5:stock.getThreshold();
        var midnight=now().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant();
        var stats=Map.of("unreadLowStock",stock.isEnabled()?data.lowStockCount(threshold):0L,"waitingReaders",data.waitingReaders(),"notificationsToday",notices.countByCreatedAtGreaterThanEqual(midnight),"failedDeliveries",notices.countByEmailState("FAILED")+notices.countByEmailState("QUEUE_FAILED"));
        return Map.of("rules",ruleRows.stream().map(this::ruleDto).collect(Collectors.toList()),"stats",stats,"lowStock",stock.isEnabled()?data.lowStock(threshold):List.of(),"demand",data.demand(),"recentRuns",runs.findTop20ByOrderByStartedAtDesc().stream().map(this::runDto).collect(Collectors.toList()));
    }
    private Map<String,Object> ruleDto(AutomationRule r){var d=new LinkedHashMap<String,Object>();d.put("key",r.getKey());d.put("label",r.getLabel());d.put("description",r.getDescription());d.put("enabled",r.isEnabled());d.put("delayHours",r.getDelayHours());d.put("threshold",r.getThreshold());return d;}
    private Map<String,Object> runDto(AutomationRun r){var d=new LinkedHashMap<String,Object>();d.put("id",r.getId());d.put("startedAt",r.getStartedAt());d.put("status",r.getStatus());d.put("createdCount",r.getCreatedCount());d.put("error",r.getError());return d;}
    @Data public static class RuleInput {private Boolean enabled;private Integer delayHours;private Integer threshold;}
    @Transactional public Map<String,Object> saveRule(String key,RuleInput input){
        requireAdmin();if(input==null||input.getEnabled()==null)throw fail(HttpStatus.BAD_REQUEST,"Bạn chọn trạng thái tự động hóa.");var r=rules.findById(key).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy luồng tự động hóa."));
        if("CART_REMINDER".equals(key)||"REVIEW_REMINDER".equals(key)){if(input.getDelayHours()==null||input.getDelayHours()<1||input.getDelayHours()>720)throw fail(HttpStatus.BAD_REQUEST,"Thời gian chờ phải từ 1 đến 720 giờ.");r.setDelayHours(input.getDelayHours());}
        if("LOW_STOCK".equals(key)){if(input.getThreshold()==null||input.getThreshold()<0||input.getThreshold()>10000)throw fail(HttpStatus.BAD_REQUEST,"Ngưỡng tồn kho phải từ 0 đến 10.000.");r.setThreshold(input.getThreshold());}
        r.setEnabled(input.getEnabled());rules.save(r);return ruleDto(r);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void recordRun(Instant started,String status,int created){var r=new AutomationRun();r.setId(UUID.randomUUID().toString());r.setStartedAt(started);r.setStatus(status);r.setCreatedCount(created);r.setError("FAILED".equals(status)?"Lần kiểm tra chưa hoàn tất. Thay đổi đã được hoàn tác; hệ thống sẽ thử lại.":null);runs.save(r);runs.deleteByStartedAtBefore(now().minus(Duration.ofDays(30)));}
}

package vn.fs.service;

import static vn.fs.service.ChatTool.Parameter.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

/** Customer actions share the catalog and reference guards of the single tool registry. */
final class ChatCustomerTools {
    private final ChatBuiltinTools owner;
    ChatCustomerTools(ChatBuiltinTools owner){this.owner=owner;}
    ChatTool prepareCheckout() {
        Map<String,ChatTool.Parameter> p=owner.referenceParameters();p.put("quantity",integer("Số cuốn khách muốn mua, giữ nguyên kể cả vượt tồn kho; backend sẽ kiểm tra",1,Integer.MAX_VALUE));
        return owner.tool("prepareCheckout","Chuẩn bị mua một sách hoặc toàn bộ giỏ (source=cart, reference=selection). Chỉ mở form và báo giá; khách phải xác nhận trước khi tạo đơn.",p,(a,c)->{
            if("cart".equals(a.path("source").asText()) && !a.hasNonNull("title") && !a.hasNonNull("productId")
                    && (a.path("reference").asText().isBlank() || "selection".equals(a.path("reference").asText()))) {
                if(c.request().getCartItems().isEmpty())return ChatTool.Result.clarify("Giỏ hàng đang trống nên mình chưa có sách để chuẩn bị đơn. Bạn chọn sách trước khi đặt hàng nhé; mình có thể giúp tìm theo nhu cầu của bạn.");
                if(owner.access!=null && owner.access.currentEmail()==null)return purchaseLogin();
                return new ChatTool.Result("Mình có thể giúp bạn đặt các sách trong giỏ. Bạn kiểm tra giỏ rồi chọn địa chỉ và phương thức thanh toán trong khung chat để mình tính đúng tổng tiền. Chưa có đơn nào được tạo; bạn sẽ xem lại trước khi xác nhận.",
                    ChatIntent.PURCHASE,List.of(),List.of(new ChatActionDto("CHECKOUT_CART","Đặt hàng từ giỏ",null,null,false)),"AWAITING_CONFIRMATION");
            }
            List<Product> selected=owner.resolve(a,c); if(selected.size()!=1)return owner.missing(c,selected);
            Product b=selected.get(0);ChatCustomerActions.Quantity requested=requestedQuantity(a,c,b);
            if(!requested.error().isBlank())return ChatTool.Result.clarify(requested.error());
            long count=requested.value();
            if(b.getQuantity()<count)return ChatTool.Result.clarify("Bạn muốn mua "+count+" cuốn “"+b.getName()+"”, nhưng tồn kho hiện tại chỉ còn "+b.getQuantity()+" cuốn. Mình chưa chuẩn bị đơn; bạn chọn số lượng không vượt tồn kho nhé.");
            if(count>99)return ChatTool.Result.clarify("Bạn muốn mua "+count+" cuốn. Mỗi thao tác đặt hàng hiện hỗ trợ tối đa 99 cuốn; bạn chọn lại số lượng hoặc liên hệ cửa hàng để mua số lượng lớn nhé. Mình chưa chuẩn bị đơn.");
            int quantity=(int)count;
            c.state().focus=b.getProductId();
            c.state().displayed=List.of(b.getProductId());c.state().shortlist=List.of(b.getProductId());c.state().emptySearch=false;
            if(owner.access!=null && owner.access.currentEmail()==null)return purchaseLogin(b,quantity);
            ChatActionDto action=new ChatActionDto("BUY_NOW","Mở lại đơn mua",b.getProductId(),null,false);
            action.setQuantity(quantity);
            return new ChatTool.Result("Mình đã chọn “"+b.getName()+"” với số lượng "+quantity+" cuốn. Bạn kiểm tra thông tin giao hàng và tổng tiền bên dưới; đơn chỉ được tạo khi bạn bấm Xác nhận đặt hàng.",ChatIntent.PURCHASE,List.of(owner.dto(b)),List.of(action),"AWAITING_CONFIRMATION");
        });
    }
    ChatTool prepareCartChange() {
        Map<String,ChatTool.Parameter> p=owner.referenceParameters();
        p.put("operation",choice("Thao tác giỏ hàng", "add","remove","update"));p.put("quantity",integer("Số lượng thêm hoặc số lượng mới, giữ nguyên kể cả vượt tồn kho; backend sẽ kiểm tra",1,Integer.MAX_VALUE));
        return owner.tool("prepareCartChange","Yêu cầu thêm/xóa/sửa số lượng trong giỏ trình duyệt. Chỉ tạo nút chờ xác nhận.",p,(a,c)->{
            List<Product> selected=owner.resolve(a,c); if(selected.size()!=1)return owner.missing(c,selected);
            Product b=selected.get(0);String operation=a.path("operation").asText("add");
            ChatCustomerActions.Quantity requested="remove".equals(operation)?new ChatCustomerActions.Quantity(false,1L,""):requestedQuantity(a,c,b);
            if(!requested.error().isBlank())return ChatTool.Result.clarify(requested.error());
            long count=requested.value();
            int inCart=c.request().getCartItems().stream().filter(i->b.getProductId().equals(i.getProductId())).mapToInt(ChatRequestDto.CartItem::getQuantity).sum();
            if(!"add".equals(operation) && inCart==0)return ChatTool.Result.clarify("Mình không thấy “"+b.getName()+"” trong giỏ hàng của bạn. Giỏ hàng chưa được thay đổi.");
            long desiredTotal="add".equals(operation)?count>Long.MAX_VALUE-inCart?Long.MAX_VALUE:count+inCart:count;
            if(!"remove".equals(operation) && b.getQuantity()<desiredTotal)return ChatTool.Result.clarify("Bạn yêu cầu "+count+" cuốn “"+b.getName()+"”"+(inCart>0?"; giỏ hiện đã có "+inCart+" cuốn":"")+", nhưng tồn kho hiện tại chỉ còn "+b.getQuantity()+" cuốn. Tổng số lượng trong giỏ sẽ vượt tồn kho hiện tại. Bạn chọn số lượng ít hơn nhé; giỏ hàng chưa được thay đổi.");
            if(!"remove".equals(operation) && desiredTotal>99)return ChatTool.Result.clarify("Tổng số lượng trong giỏ sẽ là "+desiredTotal+" cuốn, vượt mức tối đa 99 cuốn cho một sách trong giỏ. Bạn chọn lại số lượng hoặc liên hệ cửa hàng để mua số lượng lớn nhé; giỏ hàng chưa được thay đổi.");
            int quantity=(int)count;
            String type="remove".equals(operation)?"REMOVE_FROM_CART":"update".equals(operation)?"UPDATE_CART_QUANTITY":"ADD_TO_CART";
            String label="remove".equals(operation)?"Xóa khỏi giỏ":"update".equals(operation)?"Đổi thành "+quantity+" cuốn":"Thêm "+quantity+" cuốn vào giỏ";
            ChatActionDto action=new ChatActionDto(type,label,b.getProductId(),null,true);action.setQuantity(quantity);
            c.state().focus=b.getProductId();c.state().displayed=List.of(b.getProductId());c.state().emptySearch=false;
            return new ChatTool.Result("Mình đã chuẩn bị thao tác với “"+b.getName()+"”. Bạn kiểm tra rồi xác nhận để mình sửa đúng sách và số lượng bạn muốn nhé. Giỏ hàng chưa được thay đổi.",ChatIntent.PRODUCT_INFO,List.of(owner.dto(b)),List.of(action),"AWAITING_CONFIRMATION");
        });
    }
    private ChatCustomerActions.Quantity requestedQuantity(JsonNode a,ChatTool.Context c,Product book) {
        String question=owner.language.normalize(c.request().getMessage());
        String withoutTitle=withoutQuantityTitle(question,owner.language.normalize(book.getName()))
            .replaceAll("(?:cuon|sach|quyen) (?:thu|so) [0-9]+"," ");
        ChatCustomerActions.Quantity explicit=ChatCustomerActions.quantity(withoutTitle);
        if(explicit.explicit())return explicit;
        if(ChatCustomerActions.quantity(question).explicit())
            return new ChatCustomerActions.Quantity(false,1L,"");
        if(ChatBookFacts.has(withoutTitle,"so luong|\\bx[ ]*[0-9]+\\b|\\b[0-9]+\\b|(?:doi|sua|cap nhat).*\\b(?:thanh|con)\\b"))
            return new ChatCustomerActions.Quantity(true,null,"Mình chưa xác định chắc số lượng bạn muốn. Bạn gửi một số nguyên kèm ‘cuốn’, ví dụ ‘2 cuốn’, để mình chuẩn bị đúng nhé; chưa có thao tác nào được thực hiện.");
        return new ChatCustomerActions.Quantity(false,a.hasNonNull("quantity")?owner.integerLong(a,"quantity"):1L,"");
    }
    private String withoutQuantityTitle(String question,String title) {
        if(title.isBlank())return question;
        var mentions=java.util.regex.Pattern.compile("(?<![a-z0-9])"+java.util.regex.Pattern.quote(title)+"(?![a-z0-9])").matcher(question);
        while(mentions.find()) {
            String before=question.substring(0,mentions.start()),after=question.substring(mentions.end());
            // A numeric title can equal the requested count. Remove one actual
            // title mention, never a count followed by a book unit or quantity marker.
            if(title.matches("[0-9]+|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi")
                    && (after.matches("^\\s+(?:cuon|quyen|ban)(?:\\s.*|[.,!?].*|$)")
                        || before.matches(".*\\bso luong\\s*(?:la|thanh|:)?\\s*$")))continue;
            return before+" "+after;
        }
        return question;
    }
    private ChatTool.Result purchaseLogin() {
        return new ChatTool.Result("Bạn đăng nhập để mình chuẩn bị đặt hàng bằng đúng tài khoản nhé. Sau khi đăng nhập, bạn gửi lại yêu cầu mua sách để chọn địa chỉ, xem tổng tiền và xác nhận; chưa có đơn hàng nào được tạo.",ChatIntent.PURCHASE,List.of(),List.of(new ChatActionDto("LOGIN","Đăng nhập để đặt hàng",null,"/login",false)),"AWAITING_INPUT");
    }
    private ChatTool.Result purchaseLogin(Product book,int quantity) {
        ChatActionDto action=new ChatActionDto("LOGIN","Đăng nhập để đặt hàng",book.getProductId(),"/login",false);
        action.setQuantity(quantity);
        return new ChatTool.Result("Bạn đăng nhập để tiếp tục đặt "+quantity+" cuốn “"+book.getName()+"” nhé. Sau khi đăng nhập, mình sẽ mở lại lựa chọn này để bạn xem thông tin giao hàng và tổng tiền rồi xác nhận; chưa có đơn hàng nào được tạo.",
            ChatIntent.PURCHASE,List.of(owner.dto(book)),List.of(action),"AWAITING_INPUT");
    }
    ChatTool getCart() {
        return owner.tool("getCart","Xem giỏ thiết bị từ snapshot; giá lấy lại từ database.",Map.of(),(a,c)->{
            List<Product> books=owner.byIds(c.request().getCartItems().stream().map(ChatRequestDto.CartItem::getProductId).collect(Collectors.toList()));
            if(books.isEmpty())return ChatTool.Result.text("Mình chưa thấy sách đang bán trong giỏ hàng của bạn; giỏ có thể đang trống hoặc sách đã ngừng bán. Bạn mở giỏ để kiểm tra nhé, hoặc cho mình biết nhu cầu để tìm cuốn khác.",ChatIntent.UNKNOWN);
            StringBuilder out=new StringBuilder("Mình đã kiểm tra giá hiện tại. Giỏ hàng của bạn đang có:");long total=0;
            for(Product b:books) {
                int quantity=c.request().getCartItems().stream().filter(i->b.getProductId().equals(i.getProductId())).findFirst().orElseThrow().getQuantity();
                total+=owner.data.effectivePrice(b)*quantity;
                out.append("\n• “").append(b.getName()).append("” — ").append(quantity).append(" cuốn × ").append(owner.money(owner.data.effectivePrice(b)));
            }
            out.append("\nTạm tính tiền sách: ").append(owner.money(total)).append(". Bạn có thể bấm Xem và sửa giỏ để điều chỉnh. Mình sẽ kiểm tra lại phí giao hàng và số sách còn bán khi bạn xem báo giá, để tổng tiền sát với đơn thực tế.");
            return new ChatTool.Result(out.toString(),ChatIntent.UNKNOWN,List.of(),List.of(
                new ChatActionDto("SHOW_CART","Xem và sửa giỏ",null,null,false),
                new ChatActionDto("CHECKOUT_CART","Đặt hàng từ giỏ",null,null,false)),"COMPLETED");
        });
    }
    ChatTool prepareClearCart() {
        return owner.tool("prepareClearCart","Xóa toàn bộ giỏ hàng, chỉ tạo đề xuất chờ xác nhận; không yêu cầu chọn sách.",Map.of(),(a,c)->{
            if(c.request().getCartItems().isEmpty())return ChatTool.Result.text("Giỏ hàng đang trống nên không có sách để xóa. Bạn cứ giữ nguyên giỏ nhé; khi muốn chọn sách mới, mình có thể giúp.",ChatIntent.UNKNOWN);
            return new ChatTool.Result("Bạn xác nhận xóa toàn bộ sách khỏi giỏ nhé. Mình chờ bạn đồng ý để tránh bỏ nhầm những cuốn bạn vẫn muốn mua. Giỏ hàng chưa được thay đổi.",ChatIntent.UNKNOWN,List.of(),List.of(new ChatActionDto("CLEAR_CART","Xóa toàn bộ giỏ",null,null,true)),"AWAITING_CONFIRMATION");
        });
    }
    ChatTool prepareCancelOrder() {
        return owner.tool("prepareCancelOrder","Chuẩn bị hủy đơn của chính khách đã đăng nhập. Không có orderId thì dùng đơn đang hỏi hoặc đơn gần nhất. Kiểm tra trạng thái/thanh toán, chờ khách xác nhận.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            String question=owner.language.normalize(c.request().getMessage());
            if(question.matches("(?s).*\\b(?:lam sao|cach|huong dan|nhu the nao|the nao)\\b.*"))
                return ChatTool.Result.text(owner.policies.answer(c.request().getMessage()),ChatIntent.POLICY);
            try {
                Long requested=a.hasNonNull("orderId")?Long.valueOf(owner.integerLong(a,"orderId")):c.state().focusedOrderId;
                if(!a.hasNonNull("orderId") && ChatBookFacts.has(question,"gan nhat|moi nhat"))requested=null;
                Long id=owner.cancellation.preview(requested);
                c.state().focusedOrderId=id;
                ChatActionDto action=new ChatActionDto("CANCEL_ORDER","Kiểm tra và hủy đơn #"+id,null,null,true);
                action.setOrderId(id);
                return new ChatTool.Result("Mình đã kiểm tra: đơn #"+id+" có thể hủy theo trạng thái hiện tại. Bạn bấm nút bên dưới để xem lại sách và tổng đơn, rồi xác nhận nếu đúng đơn muốn hủy nhé. Mình sẽ chỉ hủy sau khi bạn xác nhận.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_CONFIRMATION");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                if(e.getStatus()==org.springframework.http.HttpStatus.UNAUTHORIZED)
                    return ChatTool.Result.text("Bạn cần đăng nhập để kiểm tra và hủy đơn của mình. Sau khi đăng nhập, hãy hỏi lại “Hủy đơn hàng giúp tôi” hoặc gửi mã đơn muốn hủy.",ChatIntent.ORDER_SUPPORT);
                return ChatTool.Result.text(e.getReason()==null?"Chưa thể hủy đơn này.":e.getReason(),ChatIntent.ORDER_SUPPORT);
            }
        });
    }
    private ChatTool.Result wishlistLogin() {
        return new ChatTool.Result("Bạn đăng nhập để mình mở đúng danh sách yêu thích của tài khoản trên thiết bị này nhé. Sau đó bạn có thể lưu sách để xem lại khi cần.",ChatIntent.PRODUCT_INFO,List.of(),
            List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)),"AWAITING_INPUT");
    }
    ChatTool prepareReorder() {
        return owner.tool("prepareReorder","Mua lại sách từ đơn cũ của chính khách: kiểm tra đơn, mở bước xem giá/tồn kho hiện tại, chờ xác nhận thêm giỏ. Không tạo đơn mới.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            try {
                Long requested=a.hasNonNull("orderId")?Long.valueOf(owner.integerLong(a,"orderId")):c.state().focusedOrderId;
                if(!a.hasNonNull("orderId") && ChatBookFacts.has(owner.language.normalize(c.request().getMessage()),"gan nhat|moi nhat"))requested=null;
                var preview=owner.orders.reorderPreview(requested); Long id=((Number)preview.get("orderId")).longValue();c.state().focusedOrderId=id;
                var action=new ChatActionDto("REORDER_ORDER","Xem sách để mua lại đơn #"+id,null,null,true);action.setOrderId(id);
                return new ChatTool.Result("Mình có thể giúp bạn mua lại sách từ đơn #"+id+". Bạn bấm nút bên dưới để kiểm tra giá và tồn kho hiện tại, chỉnh số lượng rồi xác nhận thêm vào giỏ nhé. Bước này chưa đặt đơn mới.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_CONFIRMATION");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                return new ChatTool.Result(e.getReason()==null?"Mình chưa mở được đơn đã mua. Bạn kiểm tra lại mã đơn nhé.":e.getReason(),ChatIntent.ORDER_SUPPORT,List.of(),
                    e.getStatus()==org.springframework.http.HttpStatus.UNAUTHORIZED?List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)):List.of(),"NEEDS_INPUT");
            }
        });
    }
    ChatTool prepareReturnRequest() {
        return owner.tool("prepareReturnRequest","Chuẩn bị biểu mẫu yêu cầu đổi trả cho đơn đã hoàn thành của chính khách. Cần mã đơn hoặc đơn đang hỏi; không tự chấp nhận/hoàn tiền. Khách xem lại rồi xác nhận gửi.",Map.of("orderId",integer("Mã đơn",1,9007199254740991L)),(a,c)->{
            Long id=a.hasNonNull("orderId")?Long.valueOf(owner.integerLong(a,"orderId")):c.state().focusedOrderId;
            if(id==null)return new ChatTool.Result("Bạn muốn đổi trả sách trong đơn nào? Bạn chọn đơn đã nhận trong mục Đơn hàng hoặc gửi mã đơn nhé, để mình lấy đúng sách và giúp cửa hàng kiểm tra yêu cầu của bạn.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("VIEW_ORDERS","Chọn đơn đã nhận",null,"/owner.orders",false)),"NEEDS_INPUT");
            try {
                owner.support.returnOptions(id);c.state().focusedOrderId=id;
                var action=new ChatActionDto("CREATE_RETURN_REQUEST","Soạn yêu cầu đổi trả đơn #"+id,null,null,true);action.setOrderId(id);
                return new ChatTool.Result("Mình đã tìm thấy đơn #"+id+" đã hoàn thành. Bạn mở biểu mẫu bên dưới, chọn cuốn sách, điền ngày nhận và lý do để cửa hàng xem xét đúng trường hợp nhé. Bạn sẽ kiểm tra lại trước khi gửi; cửa hàng chưa chấp nhận đổi trả hay hoàn tiền ở bước này.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_INPUT");
            } catch(org.springframework.web.server.ResponseStatusException e) {
                return new ChatTool.Result(e.getReason()==null?"Mình chưa mở được đơn cần đổi trả. Bạn kiểm tra lại mã đơn nhé.":e.getReason(),ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("VIEW_ORDERS","Kiểm tra đơn hàng",null,"/owner.orders",false)),"NEEDS_INPUT");
            }
        });
    }
    private ChatTool.Result supportLogin() {
        return new ChatTool.Result("Bạn đăng nhập để yêu cầu hỗ trợ được lưu đúng tài khoản và chỉ bạn xem được phản hồi nhé. Sau đó mình sẽ giúp bạn soạn nội dung để nhân viên hiểu rõ tình huống.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("LOGIN","Đăng nhập",null,"/login",false)),"NEEDS_INPUT");
    }
    ChatTool prepareHumanSupport() {
        return owner.tool("prepareHumanSupport","Mở bản nháp để nhờ nhân viên hỗ trợ. Khách xem/sửa tóm tắt và xác nhận gửi; phản hồi qua yêu cầu trên web, không hứa nhân viên đang trực tuyến.",Map.of("orderId",integer("Mã đơn nếu khách nói rõ",1,9007199254740991L)),(a,c)->{
            if(owner.access==null || owner.access.currentEmail()==null)return supportLogin();
            Long id=a.hasNonNull("orderId")?Long.valueOf(owner.integerLong(a,"orderId")):null;
            if(id!=null)try { owner.orders.reorderPreview(id); } catch(org.springframework.web.server.ResponseStatusException e){return ChatTool.Result.text(e.getReason(),ChatIntent.ORDER_SUPPORT);}
            var action=new ChatActionDto("CREATE_SUPPORT_REQUEST","Soạn yêu cầu cho nhân viên",null,null,true);action.setOrderId(id);
            return new ChatTool.Result("Mình sẽ giúp bạn chuyển điều cần hỗ trợ thành một yêu cầu cho nhân viên. Bạn mở bản nháp bên dưới, kiểm tra và bổ sung nội dung rồi xác nhận gửi nhé. Nhân viên sẽ phản hồi trong mục yêu cầu; mình chưa có thông tin để xác nhận ai đang trực tuyến hoặc thời gian trả lời cụ thể.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(action),"AWAITING_INPUT");
        });
    }
    ChatTool getSupportRequests() {
        return owner.tool("getSupportRequests","Mở danh sách yêu cầu hỗ trợ/đổi trả và phản hồi của chính khách trong chat. Không đưa nội dung riêng tư của ticket vào prompt hoặc history.",Map.of(),(a,c)->{
            if(owner.access==null || owner.access.currentEmail()==null)return supportLogin();
            return new ChatTool.Result("Bạn bấm nút bên dưới để xem yêu cầu đã gửi, trạng thái mới nhất và phản hồi của nhân viên nhé. Danh sách chỉ lấy từ tài khoản đang đăng nhập của bạn.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("SHOW_SUPPORT_REQUESTS","Xem yêu cầu và phản hồi",null,null,false)),"COMPLETED");
        });
    }
    ChatTool getWishlist() {
        return owner.tool("getWishlist","Xem danh sách yêu thích đang dùng trên website/thiết bị của khách đăng nhập. Không suy ra từ sở thích đọc.",Map.of(),(a,c)->{
            if(owner.access==null || owner.access.currentEmail()==null)return wishlistLogin();
            List<Product> books=owner.byIds(c.request().getWishlistProductIds());
            c.state().displayed=owner.ids(books); c.state().shortlist=owner.ids(books);
            if(books.size()==1)c.state().focus=books.get(0).getProductId();
            return books.isEmpty()?ChatTool.Result.text("Danh sách yêu thích trên thiết bị này đang trống hoặc các sách đã lưu không còn bán. Bạn tìm một cuốn muốn đọc rồi nhắn mình lưu vào yêu thích để dễ xem lại nhé.",ChatIntent.PRODUCT_INFO)
                :owner.books("Đây là các sách bạn đã lưu vào yêu thích trên website ở thiết bị này. Giá dưới đây là giá hiện tại nhé:",books,ChatIntent.PRODUCT_INFO,false);
        });
    }
    ChatTool prepareWishlistChange() {
        var parameters=owner.referenceParameters(); parameters.put("operation",choice("Thao tác yêu thích","add","remove"));
        return owner.tool("prepareWishlistChange","Chuẩn bị lưu/bỏ một sách khỏi yêu thích; dùng cùng danh sách local của website. Chỉ trả nút; chưa thay đổi dữ liệu.",parameters,(a,c)->{
            if(owner.access==null || owner.access.currentEmail()==null)return wishlistLogin();
            List<Product> selected=owner.resolve(a,c);
            if(selected.size()!=1)return owner.missing(c,selected);
            Product book=selected.get(0); boolean remove="remove".equals(a.path("operation").asText("add"));
            var action=new ChatActionDto(remove?"REMOVE_FROM_WISHLIST":"ADD_TO_WISHLIST",remove?"Bỏ yêu thích":"Lưu yêu thích",book.getProductId(),null,false);
            c.state().focus=book.getProductId();c.state().displayed=List.of(book.getProductId());
            return new ChatTool.Result("Bạn bấm “"+action.getLabel()+"” ở cuốn “"+book.getName()+"” nhé. Mình sẽ cập nhật cùng danh sách yêu thích trên website của tài khoản này, để bạn dễ tìm lại sách.",ChatIntent.PRODUCT_INFO,List.of(owner.dto(book)),List.of(action),"AWAITING_INPUT");
        });
    }
    ChatTool getMyOrders() {
        return owner.tool("getMyOrders","Tra đơn của tài khoản đăng nhập; mode=latest xem đơn gần nhất; recent xem 3 đơn. Không nhận userId/email.",Map.of("mode",choice("Phạm vi đơn","latest","recent")),
                (a,c)->{
                    if(owner.access!=null && owner.access.currentEmail()==null)return orderLogin();
                    c.state().focusedOrderId=owner.orders.latestOrderId();
                    return ChatTool.Result.text("latest".equals(a.path("mode").asText()) && c.state().focusedOrderId!=null
                        ?owner.orders.orderById(c.state().focusedOrderId):owner.orders.latestOrders(),ChatIntent.ORDER_SUPPORT);
                });
    }
    ChatTool getOrderDetail() {
        return owner.tool("getOrderDetail","Tra mã đơn, backend kiểm tra chủ sở hữu.",Map.of("orderId",integer("Mã đơn cần tra",1,9007199254740991L)),
                (a,c)->{
                    if(owner.access!=null && owner.access.currentEmail()==null)return orderLogin();
                    Long id=a.hasNonNull("orderId")?Long.valueOf(owner.integerLong(a,"orderId")):c.state().focusedOrderId;
                    if(id==null) {
                        // No authenticated/focused order: return the account-aware lookup response.
                        return ChatTool.Result.text(owner.orders.latestOrders(),ChatIntent.ORDER_SUPPORT);
                    }
                    String answer=owner.orders.orderById(id);
                    if(answer.startsWith("Đơn #"))c.state().focusedOrderId=id;
                    if (answer.startsWith("Đơn #")) return new ChatTool.Result(answer,ChatIntent.ORDER_SUPPORT,List.of(),
                        List.of(new ChatActionDto("VIEW_ORDER","Xem chi tiết đơn #"+id,null,"/owner.orders/"+id,false)),"COMPLETED");
                    return ChatTool.Result.text(answer,ChatIntent.ORDER_SUPPORT);
                });
    }
    private ChatTool.Result orderLogin() {
        return new ChatTool.Result("Bạn đăng nhập để mình tra đúng đơn hàng thuộc tài khoản của bạn nhé. Sau khi đăng nhập, bạn gửi lại câu hỏi hoặc mã đơn muốn kiểm tra.",ChatIntent.ORDER_SUPPORT,List.of(),List.of(new ChatActionDto("LOGIN","Đăng nhập để xem đơn",null,"/login",false)),"AWAITING_INPUT");
    }
}

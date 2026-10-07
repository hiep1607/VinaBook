package vn.fs.service;
import java.time.*;
import java.util.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import lombok.Data;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;

@Service
public class CustomerSupportService {
    private final CurrentUserAccess access; private final SupportRequestRepository requests;
    private final OrderRepository orders; private final ChatOrderSupportService history; private final EntityManager em;
    public CustomerSupportService(CurrentUserAccess access,SupportRequestRepository requests,OrderRepository orders,ChatOrderSupportService history,EntityManager em) {
        this.access=access;this.requests=requests;this.orders=orders;this.history=history;this.em=em;
    }
    private Long owner() { Long id=access.currentUserId();if(id==null)throw failure(HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để yêu cầu được lưu đúng tài khoản và xem được phản hồi nhé.");return id; }
    private void admin() { if(!access.isAdmin())throw failure(HttpStatus.FORBIDDEN,"Chỉ nhân viên quản trị được xử lý yêu cầu."); }
    private static ResponseStatusException failure(HttpStatus status,String reason) { return new ResponseStatusException(status,reason); }
    @Transactional(readOnly=true, noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> returnOptions(Long id) {
        var result=history.reorderPreview(id);
        Order order=orders.findById(((Number)result.get("orderId")).longValue()).orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Không tìm thấy đơn."));
        if(order.getStatus()!=2)throw failure(HttpStatus.CONFLICT,"Yêu cầu đổi trả áp dụng cho đơn đã hoàn thành. Nếu chưa nhận sách, bạn gửi yêu cầu hỗ trợ giao hàng ở trang Liên hệ nhé.");
        return result;
    }
    @Transactional
    public Map<String,Object> create(Create input) {
        Long ownerId=owner();
        if(!input.confirmed)throw failure(HttpStatus.BAD_REQUEST,"Bạn xem lại nội dung và xác nhận gửi yêu cầu nhé.");
        // Serialize requests for one owner so retries/racing clicks cannot create duplicate tickets.
        if(em.find(User.class,ownerId,LockModeType.PESSIMISTIC_WRITE)==null)throw failure(HttpStatus.UNAUTHORIZED,"Tài khoản không còn khả dụng.");
        var existing=requests.findByOwnerIdAndRequestKey(ownerId,input.requestKey);
        if(existing.isPresent()) {
            var saved=existing.get();
            if(!Objects.equals(saved.getKind(),input.kind) || !Objects.equals(saved.getOrderId(),input.orderId)
                    || !Objects.equals(saved.getProductId(),input.productId) || !Objects.equals(saved.getReceivedOn(),input.receivedOn)
                    || !Objects.equals(saved.getOutcome(),input.outcome) || !Objects.equals(saved.getMessage(),input.message.trim()))
                throw failure(HttpStatus.CONFLICT,"Nội dung đã thay đổi so với yêu cầu đang kiểm tra. Bạn xem yêu cầu đã gửi trước khi tạo yêu cầu khác nhé.");
            return dto(saved);
        }
        if(input.orderId!=null)history.reorderPreview(input.orderId);
        if("RETURN".equals(input.kind)) {
            if(input.orderId==null || input.productId==null || input.receivedOn==null || !Set.of("EXCHANGE","RETURN").contains(input.outcome==null?"":input.outcome))
                throw failure(HttpStatus.BAD_REQUEST,"Bạn chọn đơn, cuốn sách, ngày nhận thực tế và cách muốn xử lý để cửa hàng kiểm tra đúng trường hợp nhé.");
            var options=returnOptions(input.orderId);
            @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)options.get("items");
            if(items.stream().noneMatch(item->Objects.equals(item.get("productId"),input.productId)))throw failure(HttpStatus.BAD_REQUEST,"Cuốn sách chưa nằm trong đơn đã chọn.");
            Order order=orders.findById(input.orderId).orElseThrow();
            if(input.receivedOn.isAfter(LocalDate.now()) || order.getOrderDate()!=null
                    && input.receivedOn.isBefore(order.getOrderDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()))
                throw failure(HttpStatus.BAD_REQUEST,"Ngày nhận cần nằm từ ngày đặt đơn đến hôm nay. Bạn kiểm tra lại ngày nhận thực tế nhé.");
        } else if(!"SUPPORT".equals(input.kind) || input.productId!=null || input.receivedOn!=null || input.outcome!=null)
            throw failure(HttpStatus.BAD_REQUEST,"Thông tin yêu cầu hỗ trợ chưa hợp lệ.");
        var request=new SupportRequest();request.setOwnerId(ownerId);request.setRequestKey(input.requestKey);request.setKind(input.kind);
        request.setOrderId(input.orderId);request.setProductId(input.productId);request.setReceivedOn(input.receivedOn);request.setOutcome(input.outcome);
        request.setMessage(input.message.trim());request.setCreatedAt(Instant.now());request.setUpdatedAt(request.getCreatedAt());
        return dto(requests.saveAndFlush(request));
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> mine() { return requests.findTop50ByOwnerIdOrderByIdDesc(owner()).stream().map(this::dto).collect(java.util.stream.Collectors.toList()); }
    @Transactional public Map<String,Object> byKey(String key) {
        Long ownerId=owner();
        // Wait for an earlier submission by this owner to finish before reporting “not found”.
        if(em.find(User.class,ownerId,LockModeType.PESSIMISTIC_WRITE)==null)throw failure(HttpStatus.UNAUTHORIZED,"Tài khoản không còn khả dụng.");
        return dto(requests.findByOwnerIdAndRequestKey(ownerId,key).orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Chưa tìm thấy yêu cầu này trong tài khoản của bạn.")));
    }
    @Transactional(readOnly=true) public Map<String,Object> queue(String status,int page) {
        admin();if(!status.isBlank() && !Set.of("OPEN","IN_REVIEW","RESOLVED","REJECTED").contains(status))throw failure(HttpStatus.BAD_REQUEST,"Trạng thái chưa hợp lệ.");
        var pageable=PageRequest.of(Math.max(0,page),25,Sort.by("id").descending());
        var result=status.isBlank()?requests.findAll(pageable):requests.findByStatus(status,pageable);
        return Map.of("items",result.getContent().stream().map(this::dto).collect(java.util.stream.Collectors.toList()),"page",result.getNumber(),"hasNext",result.hasNext());
    }
    @Transactional public Map<String,Object> respond(Long id,Response input) {
        admin();var request=em.find(SupportRequest.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(request==null)throw failure(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu.");
        if(input.version==null || input.version!=request.getVersion())throw failure(HttpStatus.CONFLICT,"Yêu cầu đã được nhân viên khác cập nhật. Bạn tải lại trước khi phản hồi nhé.");
        if(!Set.of("IN_REVIEW","RESOLVED","REJECTED").contains(input.status))throw failure(HttpStatus.BAD_REQUEST,"Trạng thái xử lý chưa hợp lệ.");
        if(input.reply==null || input.reply.trim().isBlank())throw failure(HttpStatus.BAD_REQUEST,"Bạn nhập phản hồi để khách biết kết quả và bước tiếp theo nhé.");
        request.setStatus(input.status);request.setReply(input.reply.trim());request.setUpdatedAt(Instant.now());requests.saveAndFlush(request);
        return dto(request);
    }
    private Map<String,Object> dto(SupportRequest request) {
        var result=new LinkedHashMap<String,Object>();result.put("id",request.getId());result.put("requestKey",request.getRequestKey());
        result.put("kind",request.getKind());result.put("orderId",request.getOrderId());result.put("productId",request.getProductId());result.put("receivedOn",request.getReceivedOn());
        result.put("outcome",request.getOutcome());result.put("message",request.getMessage());result.put("status",request.getStatus());result.put("reply",request.getReply());
        result.put("createdAt",request.getCreatedAt());result.put("updatedAt",request.getUpdatedAt());result.put("version",request.getVersion());return result;
    }
    @Data public static class Create {
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{10,80}") private String requestKey;
        @NotBlank @Pattern(regexp="RETURN|SUPPORT") private String kind;
        @Positive private Long orderId;
        @Positive private Long productId;
        @PastOrPresent private LocalDate receivedOn;
        private String outcome;
        @NotBlank @Size(min=5,max=2000) private String message;
        @AssertTrue private boolean confirmed;
    }
    @Data public static class Response {
        @NotBlank @Pattern(regexp="IN_REVIEW|RESOLVED|REJECTED") private String status;
        @NotBlank @Size(max=2000) private String reply;
        @NotNull @Min(0) private Long version;
    }
}

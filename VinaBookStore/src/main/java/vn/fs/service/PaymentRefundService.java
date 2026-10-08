package vn.fs.service;
import java.time.Instant;
import java.util.*;
import javax.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.Data;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
@Service
public class PaymentRefundService {
 private final CurrentUserAccess access;private final OrderRepository orders;private final PaymentRefundRepository refunds;
 private final PaymentGatewayService gateway;private final EntityManager em;private final TransactionTemplate tx;
 public PaymentRefundService(CurrentUserAccess access,OrderRepository orders,PaymentRefundRepository refunds,PaymentGatewayService gateway,EntityManager em,PlatformTransactionManager manager) {
  this.access=access;this.orders=orders;this.refunds=refunds;this.gateway=gateway;this.em=em;this.tx=new TransactionTemplate(manager);
 }
 @Data public static class Input {private String requestKey;private Long amount;private String reason;private boolean confirmed;}
 private void admin(){if(!access.isAdmin())throw problem(HttpStatus.FORBIDDEN,"Chỉ nhân viên được xác nhận hoàn tiền.");}
 private Order order(Long id,boolean lock){Order o=id==null?null:lock?em.find(Order.class,id,LockModeType.PESSIMISTIC_WRITE):orders.findById(id).orElse(null);if(o==null)throw problem(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");return o;}
 public Map<String,Object> preview(Long id,boolean staff){return tx.execute(t->{if(staff)admin();Order o=order(id,false);if(!staff && (o.getUser()==null||!access.ownsUserId(o.getUser().getUserId())))throw problem(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng trong tài khoản của bạn.");
  List<PaymentRefund> rows=refunds.findByOrderIdOrderByIdDesc(id);long reserved=reserved(rows),paid=Math.round(o.getAmount());Map<String,Object> result=new LinkedHashMap<>();
  result.put("orderId",id);result.put("refunds",rows.stream().map(r->dto(r,staff)).collect(java.util.stream.Collectors.toList()));
  result.put("refunded",rows.stream().filter(r->"SUCCEEDED".equals(r.getStatus())).mapToLong(PaymentRefund::getAmount).sum());
  if(staff){String reason=gateway.refundUnavailable(o);result.put("remaining",Math.max(0,paid-reserved));result.put("minimum", "MOMO".equals(o.getPaymentMethod())?10000:1);result.put("available",reason.isEmpty()&&paid>reserved);result.put("reason",reason.isEmpty()&&paid<=reserved?"Toàn bộ tiền đã hoàn hoặc đang chờ đối soát.":reason);}return result;});}
 public Map<String,Object> confirm(Long id,Input input){admin();if(input==null||!input.confirmed||input.requestKey==null||!input.requestKey.matches("[a-zA-Z0-9_-]{8,80}")||input.amount==null||input.amount<1||input.reason==null||input.reason.trim().length()<5||input.reason.trim().length()>200)throw problem(HttpStatus.BAD_REQUEST,"Bạn nhập số tiền, lý do và xác nhận lại trước khi hoàn tiền nhé.");
  boolean[] created={false};PaymentRefund refund=tx.execute(t->{Order o=order(id,true);Optional<PaymentRefund> old=refunds.findByOrderIdAndRequestKey(id,input.requestKey);if(old.isPresent()){PaymentRefund r=old.get();if(r.getAmount()!=input.amount||!r.getReason().equals(input.reason.trim()))throw problem(HttpStatus.CONFLICT,"Yêu cầu đã gửi có nội dung khác. Hãy tải lại kết quả trước.");return r;}
   String unavailable=gateway.refundUnavailable(o);if(!unavailable.isEmpty())throw problem(HttpStatus.CONFLICT,unavailable);
   long remain=Math.round(o.getAmount())-reserved(refunds.findByOrderIdOrderByIdDesc(id));if(input.amount>remain)throw problem(HttpStatus.CONFLICT,"Số tiền vượt phần còn có thể hoàn của đơn.");
   if("MOMO".equals(o.getPaymentMethod())&&(input.amount<10000||input.amount>50000000))throw problem(HttpStatus.BAD_REQUEST,"Hoàn tiền MoMo ở cửa hàng này áp dụng từ 10.000đ đến 50.000.000đ.");
   PaymentRefund r=new PaymentRefund();r.setOrderId(id);r.setRequestKey(input.requestKey);r.setGatewayReference(UUID.randomUUID().toString().replace("-",""));r.setAmount(input.amount);r.setProvider(o.getPaymentMethod());r.setReason(input.reason.trim());r.setStatus("PROCESSING");r.setCreatedAt(Instant.now());r.setUpdatedAt(r.getCreatedAt());r.setStaffId(access.currentUserId());created[0]=true;return refunds.saveAndFlush(r);});
  if(created[0]){String status=gateway.refund(order(id,false),refund);tx.executeWithoutResult(t->{order(id,true);PaymentRefund saved=em.find(PaymentRefund.class,refund.getId(),LockModeType.PESSIMISTIC_WRITE);if("PROCESSING".equals(saved.getStatus())){saved.setStatus(status);saved.setUpdatedAt(Instant.now());refunds.saveAndFlush(saved);}});}
  return preview(id,true);
 }
 @Data public static class Reconcile {private String status;private String reference;private boolean confirmed;}
 public Map<String,Object> reconcile(Long id,Long refundId,Reconcile input){admin();if(input==null||!input.confirmed||!Arrays.asList("SUCCEEDED","FAILED").contains(input.status)||input.reference==null||input.reference.trim().length()<5||input.reference.length()>120)throw problem(HttpStatus.BAD_REQUEST,"Bạn nhập mã đối soát từ cổng thanh toán và xác nhận kết quả thực tế.");
  tx.executeWithoutResult(t->{order(id,true);PaymentRefund r=em.find(PaymentRefund.class,refundId,LockModeType.PESSIMISTIC_WRITE);if(r==null||!id.equals(r.getOrderId()))throw problem(HttpStatus.NOT_FOUND,"Không tìm thấy giao dịch.");
   if(!Arrays.asList("UNKNOWN","PENDING","PROCESSING").contains(r.getStatus())||r.getCreatedAt().isAfter(Instant.now().minusSeconds(60)))throw problem(HttpStatus.CONFLICT,"Giao dịch đã có kết quả hoặc còn đang được gửi. Bạn tải lại nhé.");
   r.setStatus(input.status);r.setReconciliationReference(input.reference.trim());r.setReconciledBy(access.currentUserId());r.setUpdatedAt(Instant.now());refunds.saveAndFlush(r);});return preview(id,true);
 }
 private long reserved(List<PaymentRefund> rows){return rows.stream().filter(r->!"FAILED".equals(r.getStatus())).mapToLong(PaymentRefund::getAmount).sum();}
 private Map<String,Object> dto(PaymentRefund r,boolean staff){Map<String,Object> d=new LinkedHashMap<>();d.put("id",r.getId());d.put("amount",r.getAmount());d.put("provider",r.getProvider());d.put("status",r.getStatus());d.put("createdAt",r.getCreatedAt());d.put("updatedAt",r.getUpdatedAt());if(staff){d.put("reason",r.getReason());d.put("gatewayReference",r.getGatewayReference());d.put("reconciliationReference",r.getReconciliationReference());}return d;}
 private ResponseStatusException problem(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}

package vn.fs.api;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import vn.fs.service.PaymentRefundService;
@RestController @RequestMapping("api/payments") @CrossOrigin("*")
public class PaymentRefundApi {
 private final PaymentRefundService refunds;public PaymentRefundApi(PaymentRefundService refunds){this.refunds=refunds;}
 @GetMapping("orders/{id}/refunds") public Map<String,Object> mine(@PathVariable Long id){return refunds.preview(id,false);}
 @GetMapping("admin/orders/{id}/refunds") public Map<String,Object> preview(@PathVariable Long id){return refunds.preview(id,true);}
 @PostMapping("admin/orders/{id}/refunds") public Map<String,Object> confirm(@PathVariable Long id,@RequestBody PaymentRefundService.Input input){return refunds.confirm(id,input);}
 @PostMapping("admin/orders/{id}/refunds/{refundId}/reconcile") public Map<String,Object> reconcile(@PathVariable Long id,@PathVariable Long refundId,@RequestBody PaymentRefundService.Reconcile input){return refunds.reconcile(id,refundId,input);}
}

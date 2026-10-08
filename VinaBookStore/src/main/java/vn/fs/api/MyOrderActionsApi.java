package vn.fs.api;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.service.OrderCancellationService;

@CrossOrigin(origins={"http://localhost:4200","http://localhost:4300","http://127.0.0.1:4200","http://127.0.0.1:4300"})
@RestController @RequestMapping("/api/orders/me")
public class MyOrderActionsApi {
    private final OrderCancellationService service;
    private final vn.fs.service.ChatOrderSupportService history;
    public MyOrderActionsApi(OrderCancellationService service,vn.fs.service.ChatOrderSupportService history){this.service=service;this.history=history;}
    @GetMapping("/{id}") public Map<String,Object> details(@PathVariable Long id){return history.customerDetails(id);}
    @GetMapping("/{id}/reorder") public Map<String,Object> reorder(@PathVariable Long id){return history.reorderPreview(id);}
    @GetMapping("/{id}/cancellation") public Map<String,Object> preview(@PathVariable Long id){return service.previewDetails(id);}
    @PostMapping("/{id}/cancel") public Map<String,Object> cancel(@PathVariable Long id,@Valid @RequestBody Confirmation body){return service.cancel(id);}
    @Data public static class Confirmation { @AssertTrue private boolean confirmed; }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"Không thể hủy đơn":e.getReason()));}
}

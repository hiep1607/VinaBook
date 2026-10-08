package vn.fs.api;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
@RestControllerAdvice(assignableTypes={VoucherApi.class,PaymentRefundApi.class})
public class CustomerFeatureErrors {
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> rejected(ResponseStatusException error){return ResponseEntity.status(error.getStatus()).body(Map.of("message",error.getReason()==null?"Mình chưa xử lý được yêu cầu. Bạn kiểm tra lại nhé.":error.getReason()));}
 @ExceptionHandler({DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class}) public ResponseEntity<?> changed(Exception error){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","Dữ liệu vừa được cập nhật. Bạn tải lại kết quả trước khi thử lại nhé, để tránh ghi đè hoặc gửi trùng yêu cầu."));}
}

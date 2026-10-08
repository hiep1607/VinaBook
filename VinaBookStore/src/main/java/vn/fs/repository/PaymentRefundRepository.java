package vn.fs.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.PaymentRefund;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,Long> {
 List<PaymentRefund> findByOrderIdOrderByIdDesc(Long orderId);
 Optional<PaymentRefund> findByOrderIdAndRequestKey(Long orderId,String requestKey);
}

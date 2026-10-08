package vn.fs.entity;
import javax.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="payment_refunds",uniqueConstraints=@UniqueConstraint(columnNames={"order_id","request_key"})) @Getter @Setter
public class PaymentRefund {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="order_id",nullable=false) private Long orderId;
 @Column(name="request_key",nullable=false,length=80) private String requestKey;
 @Column(nullable=false,length=32) private String gatewayReference;
 private long amount; private String provider;
 @Column(length=200) private String reason;
 private String status; private Instant createdAt; private Instant updatedAt;
 private Long staffId; private Long reconciledBy;
 @Column(length=120) private String reconciliationReference;
}

package vn.fs.entity;
import java.time.Instant;
import java.time.LocalDate;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="support_requests",uniqueConstraints=@UniqueConstraint(name="uq_support_owner_key",columnNames={"owner_id","request_key"}))
@Getter @Setter
public class SupportRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="request_key",nullable=false,length=80) private String requestKey;
    @Column(nullable=false,length=16) private String kind;
    @Column(name="order_id") private Long orderId;
    @Column(name="product_id") private Long productId;
    @Column(name="received_on") private LocalDate receivedOn;
    @Column(length=16) private String outcome;
    @Column(nullable=false,length=2000) private String message;
    @Column(nullable=false,length=16) private String status="OPEN";
    @Column(nullable=false,length=2000) private String reply="";
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
}

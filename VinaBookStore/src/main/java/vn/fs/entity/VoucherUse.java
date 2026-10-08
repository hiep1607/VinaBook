package vn.fs.entity;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="voucher_uses",uniqueConstraints=@UniqueConstraint(columnNames="order_id")) @Getter @Setter
public class VoucherUse {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="voucher_id",nullable=false) private Long voucherId;
 @Column(name="owner_id",nullable=false) private Long ownerId;
 @Column(name="order_id",nullable=false) private Long orderId;
 private long discount;
}

package vn.fs.entity;
import javax.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="vouchers") @Getter @Setter
public class Voucher {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=32) private String code;
 private String kind;private long value;private long maximumDiscount;private long minimumSubtotal;
 private Instant startsAt;private Instant expiresAt;private int usageLimit;private int perUserLimit;private boolean active;
}

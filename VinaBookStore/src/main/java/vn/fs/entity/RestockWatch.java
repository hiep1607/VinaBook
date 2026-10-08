package vn.fs.entity;
import javax.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="restock_watches",uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","product_id"})) @Getter @Setter
public class RestockWatch {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="owner_id",nullable=false) private Long ownerId;
 @Column(name="product_id",nullable=false) private Long productId;
 @Version private long revision;
 private String state;private Instant createdAt;private Instant availableAt;private boolean seen;
}

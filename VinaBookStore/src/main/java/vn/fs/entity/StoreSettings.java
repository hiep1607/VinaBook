package vn.fs.entity;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "store_settings")
@Getter @Setter @NoArgsConstructor
public class StoreSettings {
    @Id private Long id = 1L;
    @Column(name = "shipping_fee", nullable = false) private long shippingFee = 20000;
    @Column(name = "free_shipping_threshold", nullable = false) private long freeShippingThreshold = 300000;
    @Column(name = "delivery_min_days", nullable = false) private int deliveryMinDays = 2;
    @Column(name = "delivery_max_days", nullable = false) private int deliveryMaxDays = 4;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}

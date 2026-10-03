package vn.fs.entity;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "store_policies", uniqueConstraints = @UniqueConstraint(columnNames = {"policy_key","version"}))
@Getter @Setter @NoArgsConstructor
public class StorePolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "policy_key", nullable = false, length = 32) private String policyKey;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "effective_at", nullable = false) private LocalDateTime effectiveAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(nullable = false) private boolean approved;
}

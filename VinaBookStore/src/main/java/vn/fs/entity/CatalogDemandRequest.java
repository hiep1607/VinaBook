package vn.fs.entity;

import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A deduplicated book-title request, without customer identity or conversation text. */
@Entity
@Table(name = "catalog_demand_requests", indexes = {
    @Index(name = "idx_catalog_demand_time", columnList = "requested_at"),
    @Index(name = "idx_catalog_demand_title", columnList = "normalized_title")
})
@Getter @Setter
public class CatalogDemandRequest {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 140) private String title;
    @Column(name = "normalized_title", nullable = false, length = 200) private String normalizedTitle;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
}

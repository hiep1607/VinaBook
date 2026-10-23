package vn.fs.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.CatalogDemandRequest;

public interface CatalogDemandRepository extends JpaRepository<CatalogDemandRequest, String> {
    interface DemandSummary {
        String getTitle();
        Long getRequestCount();
        Instant getLastRequestedAt();
    }

    @Query("select min(d.title) as title, count(d) as requestCount, max(d.requestedAt) as lastRequestedAt " +
        "from CatalogDemandRequest d where d.requestedAt >= :since group by d.normalizedTitle " +
        "order by count(d) desc, max(d.requestedAt) desc")
    List<DemandSummary> topSince(@Param("since") Instant since, Pageable page);

    void deleteByRequestedAtBefore(Instant cutoff);
}

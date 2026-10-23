package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import vn.fs.entity.CatalogDemandRequest;
import vn.fs.repository.CatalogDemandRepository;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","spring.datasource.url=jdbc:h2:mem:catalog_demand;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
class CatalogDemandPersistenceTest {
    @Autowired CatalogDemandRepository rows;
    @Autowired EntityManager em;

    private void request(String id,String title,String normalized,Instant at) {
        CatalogDemandRequest row=new CatalogDemandRequest();row.setId(id);row.setTitle(title);row.setNormalizedTitle(normalized);row.setRequestedAt(at);rows.saveAndFlush(row);
    }

    @Test void aggregatesNormalizedTitlesAcrossSessionsAndExcludesOldRows() {
        Instant now=Instant.parse("2026-10-21T05:00:00Z");
        request("a","Đường đến đảo","duong den dao",now.minusSeconds(60));
        request("b","đường đến đảo","duong den dao",now);
        request("c","Sách khác","sach khac",now.minus(Duration.ofDays(40)));
        em.clear();
        var result=rows.topSince(now.minus(Duration.ofDays(30)),PageRequest.of(0,50));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestCount()).isEqualTo(2);
        assertThat(result.get(0).getLastRequestedAt()).isEqualTo(now);
    }

    @Test void databaseKeySurvivesNewRepositoryReadsAndReportPagination() {
        Instant now=Instant.parse("2026-10-21T05:00:00Z");
        request("same-key","Sách A","sach a",now);
        request("other-key","Sách B","sach b",now.minusSeconds(60));
        em.clear();
        assertThat(rows.existsById("same-key")).isTrue();
        assertThat(rows.topSince(now.minusSeconds(120),PageRequest.of(0,1))).hasSize(1);
    }
}

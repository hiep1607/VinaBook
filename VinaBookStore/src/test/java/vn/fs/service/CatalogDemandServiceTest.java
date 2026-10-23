package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.CatalogDemandRequest;
import vn.fs.entity.Product;
import vn.fs.repository.CatalogDemandRepository;

class CatalogDemandServiceTest {
    CatalogDemandRepository rows;
    CurrentUserAccess access;
    CatalogDemandService service;
    Instant now = Instant.parse("2026-10-21T05:00:00Z");
    String session = "catalog-demand-session";
    String title = "Đường đến hòn đảo bí ẩn";

    @BeforeEach void setup() {
        rows = mock(CatalogDemandRepository.class);
        access = mock(CurrentUserAccess.class);
        service = new CatalogDemandService(rows, access, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void storesOnlyExplicitTitleAndHashedDeduplicationKey() {
        service.observe(session, "Có sách “" + title + "” không?", title, List.of());
        ArgumentCaptor<CatalogDemandRequest> saved = ArgumentCaptor.forClass(CatalogDemandRequest.class);
        verify(rows).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo(title);
        assertThat(saved.getValue().getId()).hasSize(64).doesNotContain(session);
        assertThat(saved.getValue().getNormalizedTitle()).isEqualTo("duong den hon dao bi an");
        assertThat(saved.getValue().getRequestedAt()).isEqualTo(now);
    }

    @Test void oneSessionTitleDayDoesNotCountRepeatedRequests() {
        when(rows.existsById(anyString())).thenReturn(false, true);
        service.observe(session, "Có sách “" + title + "” không?", title, List.of());
        service.observe(session, "Có sách “" + title + "” không?", title, List.of());
        verify(rows, times(1)).saveAndFlush(any());
    }

    @Test void differentSessionsOrDaysHaveDifferentKeys() {
        service.observe(session, "Có sách “" + title + "” không?", title, List.of());
        service.observe(session + "2", "Có sách “" + title + "” không?", title, List.of());
        new CatalogDemandService(rows, access, Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC))
            .observe(session, "Có sách “" + title + "” không?", title, List.of());
        ArgumentCaptor<CatalogDemandRequest> saved = ArgumentCaptor.forClass(CatalogDemandRequest.class);
        verify(rows, times(3)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().stream().map(CatalogDemandRequest::getId).distinct().count()).isEqualTo(3);
    }

    @Test void refusesUnrelatedOrPreviousTurnTitle() {
        service.observe(session, "Bạn có giúp mình không?", title, List.of());
        service.observe(session, "Có sách “Cuốn sách khác” không?", title, List.of());
        verify(rows, never()).saveAndFlush(any());
    }

    @Test void refusesContactInfoLinksNumbersAndMarkupInsteadOfAbookTitle() {
        for (String unsafe : List.of("Email test@example.com", "Liên hệ 090 123 4567", "https://example.com", "<script>tên sách</script>", "Tên sách\nemail")) {
            service.observe(session, "Có sách “" + unsafe + "” không?", unsafe, List.of());
        }
        verify(rows, never()).saveAndFlush(any());
    }

    @Test void refusesUnknownSessionAndUnavailableCatalog() {
        service.observe(null, "Có sách “" + title + "” không?", title, List.of());
        service.observe("short", "Có sách “" + title + "” không?", title, List.of());
        service.observe(session, "Có sách “" + title + "” không?", title, null);
        verify(rows, never()).saveAndFlush(any());
    }

    @Test void filteredOutExistingBookDoesNotBecomeUnmetDemand() {
        Product book = new Product();
        book.setName(title); book.setProductId(1L);
        service.observe(session, "Có sách “" + title + "” không?", title, List.of(book));
        verify(rows, never()).saveAndFlush(any());
    }

    @Test void customerCannotReadAggregate() {
        assertThatThrownBy(service::summary).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatus().value()).isEqualTo(403));
        verify(rows, never()).topSince(any(), any());
    }

    @Test void adminGetsBoundedThirtyDayAggregateWithoutIdentities() {
        when(access.isAdmin()).thenReturn(true);
        CatalogDemandRepository.DemandSummary summary = mock(CatalogDemandRepository.DemandSummary.class);
        when(summary.getTitle()).thenReturn(title);
        when(summary.getRequestCount()).thenReturn(3L);
        when(summary.getLastRequestedAt()).thenReturn(now);
        when(rows.topSince(any(), any())).thenReturn(List.of(summary));
        Map<String, Object> report = service.summary();
        assertThat(report).containsEntry("days", 30);
        assertThat(report.toString()).contains(title, "requestCount=3").doesNotContain(session, "ownerId", "email");
        verify(rows).topSince(eq(now.minus(Duration.ofDays(30))), argThat(page -> page.getPageSize() == 50));
    }

    @Test void purgesOldRecordsAfterThirtyDays() {
        service.purge();
        verify(rows).deleteByRequestedAtBefore(now.minus(Duration.ofDays(30)));
    }
}

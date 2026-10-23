package vn.fs.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.CatalogDemandRequest;
import vn.fs.entity.Product;
import vn.fs.repository.CatalogDemandRepository;

@Service
public class CatalogDemandService {
    private static final Pattern SESSION = Pattern.compile("[A-Za-z0-9_-]{12,64}");
    private static final Pattern UNSAFE = Pattern.compile("(?iu)(?:https?://|www\\.|@|\\d[\\s().-]*\\d[\\s().-]*\\d[\\s().-]*\\d[\\s().-]*\\d[\\s().-]*\\d|[\\p{Cc}<>\\[\\]{}])");
    private final CatalogDemandRepository rows;
    private final CurrentUserAccess access;
    private final Clock clock;
    private final ChatIntentService language = new ChatIntentService();

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogDemandService(CatalogDemandRepository rows, CurrentUserAccess access) {
        this(rows, access, Clock.systemUTC());
    }

    CatalogDemandService(CatalogDemandRepository rows, CurrentUserAccess access, Clock clock) {
        this.rows = rows;
        this.access = access;
        this.clock = clock;
    }

    /** Called only after a successful catalog read with an explicitly missing book title. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void observe(String sessionId, String message, String missingTitle, List<Product> catalog) {
        if (sessionId == null || !SESSION.matcher(sessionId).matches() || catalog == null || message == null || missingTitle == null) { return; }
        ChatCatalogQuery query = new ChatCatalogQuery(language, null);
        String explicit = query.explicitTitleLookup(message);
        if (explicit.isBlank()) {
            String normalizedMessage = language.normalize(message);
            if (!normalizedMessage.matches("(?s).*(?:\\bsach\\b|\\bcuon\\b|\\bquyen\\b).*")) { return; }
            if (!normalizedMessage.matches("(?s).*(?:\\bco\\b|\\btim\\b|\\bban\\b).*")) { return; }
            explicit = query.quoted(message);
        }
        String title = missingTitle.strip().replaceAll("\\s+", " ");
        String normalized = language.normalize(title);
        if (title.length() < 4 || title.length() > 140 || normalized.length() > 200 || UNSAFE.matcher(missingTitle).find()) { return; }
        if (!normalized.equals(language.normalize(explicit))) { return; }
        // A restrictive price/topic filter must not turn an existing title into missing demand.
        if (catalog.stream().anyMatch(p -> language.normalize(Objects.toString(p.getName(), "")).equals(normalized))) { return; }
        if (!query.named(title, catalog).isEmpty()) { return; }
        String id = digest(LocalDate.now(clock) + "|" + normalized + "|" + sessionId);
        if (rows.existsById(id)) { return; }
        CatalogDemandRequest row = new CatalogDemandRequest();
        row.setId(id);
        row.setTitle(title);
        row.setNormalizedTitle(normalized);
        row.setRequestedAt(clock.instant());
        rows.saveAndFlush(row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        if (!access.isAdmin()) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được xem nhu cầu nhập sách."); }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CatalogDemandRepository.DemandSummary row : rows.topSince(clock.instant().minus(Duration.ofDays(30)), PageRequest.of(0, 50))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", row.getTitle());
            item.put("requestCount", row.getRequestCount());
            item.put("lastRequestedAt", row.getLastRequestedAt());
            result.add(item);
        }
        return Map.of("days", 30, "items", result);
    }

    @Scheduled(fixedDelay = 86400000, initialDelay = 86400000)
    @Transactional
    public void purge() { rows.deleteByRequestedAtBefore(clock.instant().minus(Duration.ofDays(30))); }

    private String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}

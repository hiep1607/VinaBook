package vn.fs.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.service.CatalogDemandService;

@RestController
@RequestMapping("/api/admin/catalog-demand")
@CrossOrigin(origins = {"http://localhost:4300", "http://127.0.0.1:4300"})
public class CatalogDemandApi {
    private final CatalogDemandService service;
    public CatalogDemandApi(CatalogDemandService service) { this.service = service; }
    @GetMapping public Map<String, Object> get() { return service.summary(); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> failure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("message", "Chỉ quản trị viên được xem nhu cầu nhập sách."));
    }
}

package vn.fs.automation;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin/automations") @PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins={"http://localhost:4200","http://localhost:4300","http://127.0.0.1:4200","http://127.0.0.1:4300"})
public class AutomationAdminApi {
    private final AutomationService service;private final AutomationRunner runner;
    public AutomationAdminApi(AutomationService service,AutomationRunner runner){this.service=service;this.runner=runner;}
    @GetMapping public Map<String,Object> get(){return service.admin();}
    @PutMapping("/rules/{key}") public Map<String,Object> save(@PathVariable String key,@RequestBody AutomationService.RuleInput input){return service.saveRule(key,input);}
    @PostMapping("/run") public Map<String,Object> run(){runner.manual();return service.admin();}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"Chưa cập nhật được tự động hóa.":e.getReason()));}
}

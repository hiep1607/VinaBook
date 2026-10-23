package vn.fs.automation;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/automations/me") @PreAuthorize("hasRole('USER')")
@CrossOrigin(origins={"http://localhost:4200","http://localhost:4300","http://127.0.0.1:4200","http://127.0.0.1:4300"})
public class AutomationApi {
    private final AutomationService service;
    public AutomationApi(AutomationService service){this.service=service;}
    @GetMapping("/inbox") public Map<String,Object> inbox(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="ALL") String type,@RequestParam(defaultValue="false") boolean unread){return service.inbox(page,size,type,unread);}
    @GetMapping("/summary") public Map<String,Long> summary(){return service.summary();}
    @PutMapping("/inbox/seen") public void seenAll(){service.seenAll();}
    @PutMapping("/inbox/{id}/seen") public void seen(@PathVariable String id){service.seen(id);}
    @DeleteMapping("/inbox/{id}") public void remove(@PathVariable String id){service.remove(id);}
    @GetMapping("/preferences") public Map<String,Object> preferences(){return service.getPreferences();}
    @PutMapping("/preferences") public Map<String,Object> save(@RequestBody AutomationService.PreferenceInput input){return service.savePreferences(input);}
    @GetMapping("/authors") public List<String> authors(){return service.availableAuthors();}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"Chưa cập nhật được tự động hóa.":e.getReason()));}
}

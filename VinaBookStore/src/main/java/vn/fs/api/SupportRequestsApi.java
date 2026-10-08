package vn.fs.api;
import java.util.*;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.service.CustomerSupportService;
@CrossOrigin(origins={"http://localhost:4200","http://localhost:4300","http://127.0.0.1:4200","http://127.0.0.1:4300"})
@RestController @RequestMapping("/api/support")
public class SupportRequestsApi {
    private final CustomerSupportService service;
    public SupportRequestsApi(CustomerSupportService service){this.service=service;}
    @GetMapping("/me/{id}/messages") public List<Map<String,Object>> messages(@PathVariable Long id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="0") long before){return service.threadMessages(id,after,before,false);}
    @PostMapping("/me/{id}/messages") public Map<String,Object> send(@PathVariable Long id,@Valid @RequestBody CustomerSupportService.Message body){return service.sendMessage(id,body,false);}
    @GetMapping("/admin/{id}/messages") public List<Map<String,Object>> adminMessages(@PathVariable Long id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="0") long before){return service.threadMessages(id,after,before,true);}
    @PostMapping("/admin/{id}/messages") public Map<String,Object> adminSend(@PathVariable Long id,@Valid @RequestBody CustomerSupportService.Message body){return service.sendMessage(id,body,true);}
    @GetMapping("/me") public List<Map<String,Object>> mine(){return service.mine();}
    @PostMapping("/me") public Map<String,Object> create(@Valid @RequestBody CustomerSupportService.Create body){return service.create(body);}
    @GetMapping("/me/order/{id}") public Map<String,Object> options(@PathVariable Long id){return service.returnOptions(id);}
    @GetMapping("/me/request/{key}") public Map<String,Object> byKey(@PathVariable String key){return service.byKey(key);}
    @GetMapping("/admin") public Map<String,Object> queue(@RequestParam(defaultValue="OPEN") String status,@RequestParam(defaultValue="0") int page){return service.queue(status,page);}
    @PutMapping("/admin/{id}") public Map<String,Object> respond(@PathVariable Long id,@Valid @RequestBody CustomerSupportService.Response body){return service.respond(id,body);}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"Chưa xử lý được yêu cầu.":e.getReason()));}
}

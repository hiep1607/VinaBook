package vn.fs.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.fs.service.RestockWatchService;
@RestController @CrossOrigin("*") @RequestMapping("api/restock/me")
public class RestockWatchApi {
 private final RestockWatchService service;public RestockWatchApi(RestockWatchService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(){return service.list();}
 @GetMapping("{id}") public Map<String,Object> get(@PathVariable Long id){return service.get(id);}
 @PutMapping("{id}") public Map<String,Object> watch(@PathVariable Long id){return service.watch(id);}
 @DeleteMapping("{id}") public void remove(@PathVariable Long id){service.remove(id);}
 @PutMapping("{id}/seen") public void seen(@PathVariable Long id){service.seen(id);}
}

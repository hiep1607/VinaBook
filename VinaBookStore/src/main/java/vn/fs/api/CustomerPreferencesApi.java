package vn.fs.api;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.service.CustomerPreferencesService;
@CrossOrigin(origins={"http://localhost:4200","http://localhost:4300","http://127.0.0.1:4200","http://127.0.0.1:4300"})
@RestController @RequestMapping("/api/preferences/me")
public class CustomerPreferencesApi {
    private final CustomerPreferencesService service;
    public CustomerPreferencesApi(CustomerPreferencesService service){this.service=service;}
    @GetMapping public Map<String,Object> get(){return service.get();}
    @PostMapping("/migrate") public Map<String,Object> migrate(@Valid @RequestBody CustomerPreferencesService.Migration input){return service.migrate(input);}
    @PutMapping("/addresses") public Map<String,Object> addresses(@Valid @RequestBody CustomerPreferencesService.AddressUpdate input){return service.saveAddresses(input);}
    @PutMapping("/avatar") public Map<String,Object> avatar(@Valid @RequestBody CustomerPreferencesService.AvatarUpdate input){return service.saveAvatar(input);}
    @PutMapping("/wishlist/{id}") public Map<String,Object> add(@PathVariable Long id){return service.wishlist(id,true);}
    @DeleteMapping("/wishlist/{id}") public Map<String,Object> remove(@PathVariable Long id){return service.wishlist(id,false);}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getReason()==null?"Chưa đồng bộ được dữ liệu.":e.getReason()));}
}

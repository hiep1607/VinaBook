package vn.fs.api;
import java.util.*;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.entity.Voucher;
import vn.fs.service.*;
import vn.fs.repository.ProductRepository;
@RestController @RequestMapping("api/vouchers") @CrossOrigin("*")
public class VoucherApi {
 private final VoucherService vouchers;private final ProductRepository products;private final ChatRetrievalService pricing;
 public VoucherApi(VoucherService vouchers,ProductRepository products,ChatRetrievalService pricing){this.vouchers=vouchers;this.products=products;this.pricing=pricing;}
 @Data public static class Preview {private String code;private List<CartSyncService.Line> items;}
 @PostMapping("me/preview") public Map<String,Object> preview(@RequestBody Preview input){if(input==null||input.getItems()==null||input.getItems().isEmpty()||input.getItems().size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bạn chọn sách trước khi áp dụng mã nhé.");Set<Long> seen=new HashSet<>();long subtotal=0;
  for(var item:input.getItems()){if(item==null||item.getProductId()==null||!seen.add(item.getProductId())||item.getQuantity()<1||item.getQuantity()>99)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Danh sách sách hoặc số lượng chưa hợp lệ.");var p=products.findById(item.getProductId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!Boolean.TRUE.equals(p.getStatus())||p.getQuantity()<item.getQuantity())throw new ResponseStatusException(HttpStatus.CONFLICT,"Sách không còn đủ tồn kho. Bạn xem lại giỏ nhé.");subtotal+=pricing.effectivePrice(p)*item.getQuantity();}
  String code=VoucherService.code(input.getCode());return Map.of("code",code,"subtotal",subtotal,"discount",vouchers.preview(code,subtotal));
 }
 @GetMapping("admin") public List<Voucher> list(){return vouchers.list();}
 @PutMapping("admin") public Voucher save(@RequestBody Voucher input){return vouchers.save(input);}
}

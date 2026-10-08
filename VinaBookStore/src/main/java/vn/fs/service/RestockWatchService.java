package vn.fs.service;
import java.time.Instant;
import java.util.*;
import javax.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
@Service
public class RestockWatchService {
 private final CurrentUserAccess access;private final RestockWatchRepository watches;private final ProductRepository products;private final EntityManager em;
 public RestockWatchService(CurrentUserAccess access,RestockWatchRepository watches,ProductRepository products,EntityManager em){this.access=access;this.watches=watches;this.products=products;this.em=em;}
 private Long owner(){Long id=access.currentUserId();if(id==null)throw error(HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để thông báo được lưu đúng tài khoản nhé.");return id;}
 private Long lockOwner(){Long id=owner();if(em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE)==null)throw error(HttpStatus.UNAUTHORIZED,"Phiên tài khoản chưa hợp lệ.");return id;}
 private Product product(Long id){if(id==null||id<1)throw error(HttpStatus.BAD_REQUEST,"Bạn chọn một cuốn sách cụ thể nhé.");Product p=products.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy cuốn sách này."));if(!Boolean.TRUE.equals(p.getStatus()))throw error(HttpStatus.NOT_FOUND,"Sách hiện không được bán.");return p;}
 @Transactional public Map<String,Object> get(Long productId){Long id=owner();Product p=product(productId);RestockWatch watch=watches.findByOwnerIdAndProductId(id,productId).orElse(null);if(watch!=null)available(watch,p);return dto(watch,p);}
 @Transactional public Map<String,Object> watch(Long productId){Long owner=lockOwner();Product p=product(productId);RestockWatch row=watches.findByOwnerIdAndProductId(owner,productId).orElse(null);
  if(p.getQuantity()>0)throw error(HttpStatus.CONFLICT,"Sách đang có hàng. Bạn có thể mở sách để mua ngay nhé.");
  if(row!=null&&"WAITING".equals(row.getState()))return dto(row,p);
  if(row==null){if(watches.findTop100ByOwnerIdAndStateNotOrderByIdDesc(owner,"CANCELLED").size()>=100)throw error(HttpStatus.CONFLICT,"Bạn đã theo dõi 100 cuốn. Hãy bỏ một cuốn trước khi thêm nhé.");row=new RestockWatch();row.setOwnerId(owner);row.setProductId(productId);row.setCreatedAt(Instant.now());}
  row.setState("WAITING");row.setSeen(false);row.setAvailableAt(null);return dto(watches.save(row),p);
 }
 @Transactional public void remove(Long productId){Long id=lockOwner();watches.findByOwnerIdAndProductId(id,productId).ifPresent(row->{row.setState("CANCELLED");row.setSeen(true);watches.save(row);});}
 @Transactional public List<Map<String,Object>> list(){Long id=owner();List<Map<String,Object>> result=new ArrayList<>();for(var row:watches.findTop100ByOwnerIdAndStateNotOrderByIdDesc(id,"CANCELLED")){if("CANCELLED".equals(row.getState()))continue;Product p=products.findById(row.getProductId()).orElse(null);available(row,p);result.add(dto(row,p));}return result;}
 @Transactional public void seen(Long productId){Long id=lockOwner();watches.findByOwnerIdAndProductId(id,productId).ifPresent(row->{row.setSeen(true);watches.save(row);});}
 @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional public void scan(){for(var candidate:watches.ready(PageRequest.of(0,200))){RestockWatch row=em.find(RestockWatch.class,candidate.getId(),LockModeType.PESSIMISTIC_WRITE);if(row!=null&&"WAITING".equals(row.getState()))available(row,products.findById(row.getProductId()).orElse(null));}}
 private void available(RestockWatch row,Product p){if(p!=null&&Boolean.TRUE.equals(p.getStatus())&&p.getQuantity()>0&&"WAITING".equals(row.getState())){row.setState("AVAILABLE");row.setAvailableAt(Instant.now());row.setSeen(false);watches.save(row);}}
 private Map<String,Object> dto(RestockWatch row,Product p){Map<String,Object> d=new LinkedHashMap<>();d.put("productId",row==null?p.getProductId():row.getProductId());d.put("name",p==null?"Sách không còn trong catalog":p.getName());d.put("state",row==null?"NONE":row.getState());d.put("seen",row!=null&&row.isSeen());d.put("availableAt",row==null?null:row.getAvailableAt());d.put("inStock",p!=null&&Boolean.TRUE.equals(p.getStatus())&&p.getQuantity()>0);return d;}
 private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}

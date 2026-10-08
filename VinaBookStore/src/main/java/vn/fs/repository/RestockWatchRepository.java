package vn.fs.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.RestockWatch;
public interface RestockWatchRepository extends JpaRepository<RestockWatch,Long> {
 Optional<RestockWatch> findByOwnerIdAndProductId(Long ownerId,Long productId);
 List<RestockWatch> findTop100ByOwnerIdAndStateNotOrderByIdDesc(Long ownerId,String state);
 @org.springframework.data.jpa.repository.Query("select w from RestockWatch w, Product p where w.productId=p.productId and w.state='WAITING' and p.status=true and p.quantity>0 order by w.id")
 List<RestockWatch> ready(org.springframework.data.domain.Pageable page);
}

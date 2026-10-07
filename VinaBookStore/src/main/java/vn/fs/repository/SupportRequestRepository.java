package vn.fs.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.SupportRequest;
public interface SupportRequestRepository extends JpaRepository<SupportRequest,Long> {
    Optional<SupportRequest> findByOwnerIdAndRequestKey(Long ownerId,String requestKey);
    Optional<SupportRequest> findByIdAndOwnerId(Long id,Long ownerId);
    List<SupportRequest> findTop50ByOwnerIdOrderByIdDesc(Long ownerId);
    Page<SupportRequest> findByStatus(String status,Pageable pageable);
}

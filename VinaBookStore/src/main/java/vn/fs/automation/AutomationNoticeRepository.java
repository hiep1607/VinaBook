package vn.fs.automation;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AutomationNoticeRepository extends JpaRepository<AutomationNotice,String> {
    Optional<AutomationNotice> findByIdAndOwnerIdAndDeletedFalse(String id,Long ownerId);
    Optional<AutomationNotice> findByMailJobId(String mailJobId);
    boolean existsByOwnerIdAndEventKey(Long ownerId,String eventKey);
    long countByOwnerIdAndSeenFalseAndDeletedFalse(Long ownerId);
    long countByCreatedAtGreaterThanEqual(Instant since);
    long countByEmailState(String state);
    @Query("select n from AutomationNotice n where n.ownerId=:owner and n.deleted=false and (:type='ALL' or n.type=:type) and (:unread=false or n.seen=false)")
    Page<AutomationNotice> inbox(@Param("owner") Long owner,@Param("type") String type,@Param("unread") boolean unread,Pageable page);
    @Modifying @Query("update AutomationNotice n set n.seen=true where n.ownerId=:owner and n.deleted=false and n.seen=false")
    int markAllSeen(@Param("owner") Long owner);
}

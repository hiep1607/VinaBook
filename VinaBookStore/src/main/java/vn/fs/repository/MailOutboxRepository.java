package vn.fs.repository;

import java.util.List;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.MailOutbox;

public interface MailOutboxRepository extends JpaRepository<MailOutbox,String> {
    long countByStateIn(List<String> states);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MailOutbox m where m.dueAt <= :now and (m.state = 'PENDING' or (m.state = 'SENDING' and m.claimedAt < :stale)) order by m.dueAt,m.id")
    List<MailOutbox> ready(@Param("now") long now,@Param("stale") long stale,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MailOutbox m where m.id = :id")
    java.util.Optional<MailOutbox> locked(@Param("id") String id);
    @Modifying @Query("delete from MailOutbox m where m.createdAt < :before and m.state in ('SENT','FAILED','CANCELLED')")
    int purge(@Param("before") long before);
}

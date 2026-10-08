package vn.fs.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.SupportMessage;
public interface SupportMessageRepository extends JpaRepository<SupportMessage,Long> {
    Optional<SupportMessage> findByRequestIdAndSenderIdAndClientKey(Long requestId,Long senderId,String clientKey);
    List<SupportMessage> findTop50ByRequestIdAndIdGreaterThanOrderByIdAsc(Long requestId,Long after);
    List<SupportMessage> findTop50ByRequestIdOrderByIdDesc(Long requestId);
    List<SupportMessage> findTop50ByRequestIdAndIdLessThanOrderByIdDesc(Long requestId,Long before);
    Optional<SupportMessage> findTopByRequestIdAndSenderIdOrderByIdDesc(Long requestId,Long senderId);
}

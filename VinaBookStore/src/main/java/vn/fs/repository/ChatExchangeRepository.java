package vn.fs.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.ChatExchange;

public interface ChatExchangeRepository extends JpaRepository<ChatExchange, Long> {
    List<ChatExchange> findByConversationIdOrderByIdAsc(String conversationId);
    List<ChatExchange> findTop25ByConversationIdOrderByIdDesc(String conversationId);
    List<ChatExchange> findTop8ByConversationIdOrderByIdDesc(String conversationId);
    @Query("select e from ChatExchange e where e.conversationId=:conversationId and (:before is null or e.id<:before) order by e.id desc")
    List<ChatExchange> recent(@Param("conversationId") String conversationId,@Param("before") Long before,org.springframework.data.domain.Pageable page);
    @Query("select e from ChatExchange e where e.conversationId=:conversationId and e.id<:before and lower(e.userText) like :term order by e.id desc")
    List<ChatExchange> relevant(@Param("conversationId") String conversationId,@Param("before") Long before,@Param("term") String term,org.springframework.data.domain.Pageable page);
    @Modifying @Query("delete from ChatExchange e where e.conversationId in (select c.id from ChatConversation c where c.userId is null and c.updatedAt<:cutoff)")
    void deleteExpiredGuestExchanges(@Param("cutoff") java.time.LocalDateTime cutoff);
    Optional<ChatExchange> findByConversationIdAndRequestId(String conversationId, String requestId);
    @Modifying
    @Query("delete from ChatExchange e where e.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}

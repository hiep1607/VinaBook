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
    List<ChatExchange> findTop500ByConversationIdOrderByIdDesc(String conversationId);
    Optional<ChatExchange> findByConversationIdAndRequestId(String conversationId, String requestId);
    @Modifying
    @Query("delete from ChatExchange e where e.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}

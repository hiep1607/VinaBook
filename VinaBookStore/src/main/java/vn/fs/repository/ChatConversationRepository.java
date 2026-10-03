package vn.fs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.ChatConversation;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, String> {
    @Modifying
    @Query("delete from ChatConversation c where c.id = :id")
    void deleteIfPresent(@Param("id") String id);
}

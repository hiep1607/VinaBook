package vn.fs.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "chat_exchanges", indexes = @Index(name = "idx_chat_exchange_conversation", columnList = "conversation_id,id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_exchange_request", columnNames = {"conversation_id", "request_id"}))
public class ChatExchange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "conversation_id", length = 100, nullable = false)
    private String conversationId;
    @Column(name = "request_id", length = 64, nullable = false)
    private String requestId;
    @Column(name = "user_text", length = 1000, nullable = false)
    private String userText;
    @Column(name = "response_json", columnDefinition = "LONGTEXT", nullable = false)
    private String responseJson;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getUserText() { return userText; }
    public void setUserText(String userText) { this.userText = userText; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

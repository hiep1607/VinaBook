package vn.fs.entity;
import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="support_messages",uniqueConstraints=@UniqueConstraint(name="uq_support_message_key",columnNames={"request_id","sender_id","client_key"}))
@Getter @Setter
public class SupportMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="request_id",nullable=false) private Long requestId;
    @Column(name="sender_id",nullable=false) private Long senderId;
    @Column(name="client_key",nullable=false,length=80) private String clientKey;
    @Column(nullable=false) private boolean staff;
    @Column(nullable=false,length=2000) private String content;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}

package vn.fs.entity;

import javax.persistence.*;
import lombok.Data;

@Data @Entity
@Table(name="mail_outbox",indexes=@Index(name="idx_mail_outbox_due",columnList="state,due_at"))
public class MailOutbox {
    @Id @Column(length=36) private String id;
    @Column(name="payload",columnDefinition="LONGTEXT",nullable=false) private String payload;
    @Column(length=12,nullable=false) private String state="PENDING";
    private int attempts;
    @Column(name="due_at",nullable=false) private long dueAt;
    @Column(name="claimed_at") private long claimedAt;
    @Column(name="claim_token",length=36) private String claimToken;
    @Column(name="created_at",nullable=false) private long createdAt;
    @Version private long version;
}

package vn.fs.automation;

import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="automation_notices", uniqueConstraints=@UniqueConstraint(name="uq_automation_notice_event", columnNames={"owner_id","event_key"}),
    indexes={@Index(name="idx_automation_notice_owner",columnList="owner_id,deleted,created_at"),@Index(name="idx_automation_notice_unread",columnList="owner_id,seen,deleted"),@Index(name="idx_automation_notice_mail",columnList="mail_job_id")})
@Getter @Setter
public class AutomationNotice {
    @Id @Column(length=36) private String id;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="event_key",nullable=false,length=160) private String eventKey;
    @Column(nullable=false,length=24) private String type;
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=1000) private String message;
    @Column(nullable=false,length=160) private String link;
    @Column(name="product_id") private Long productId;
    @Column(name="order_id") private Long orderId;
    @Column(nullable=false) private boolean seen;
    @Column(nullable=false) private boolean deleted;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="source_reference",length=80) private String sourceReference;
    @Column(name="email_state",nullable=false,length=20) private String emailState="NOT_REQUESTED";
    @Column(name="mail_job_id",length=36) private String mailJobId;
    @Column(name="email_recipient_hash",length=64) private String emailRecipientHash;
}

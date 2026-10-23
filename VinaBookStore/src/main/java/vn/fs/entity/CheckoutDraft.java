package vn.fs.entity;

import javax.persistence.*;
import lombok.Data;

@Data @Entity @Table(name="checkout_drafts",indexes={@Index(name="idx_checkout_draft_owner",columnList="owner_email"),@Index(name="idx_checkout_draft_expiry",columnList="expires_at")})
public class CheckoutDraft {
    @Id @Column(length=36) private String token;
    @Column(name="owner_email",nullable=false) private String ownerEmail;
    @Column(name="payload_json",columnDefinition="LONGTEXT",nullable=false) private String payloadJson;
    @Column(name="expires_at",nullable=false) private long expiresAt;
    @Column(name="order_id") private Long orderId;
    @Version private Long version;
}

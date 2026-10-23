package vn.fs.entity;

import java.time.Instant;
import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name="password_reset_tokens", indexes=@Index(name="idx_reset_expiry",columnList="expires_at"))
public class PasswordResetToken {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="token_hash",length=64,nullable=false,unique=true) private String tokenHash;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
}

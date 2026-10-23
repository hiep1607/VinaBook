package vn.fs.repository;

import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import vn.fs.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> {
    Optional<PasswordResetToken> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :hash")
    Optional<PasswordResetToken> lockByHash(@org.springframework.data.repository.query.Param("hash") String hash);
}

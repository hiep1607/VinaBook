package vn.fs.repository;

import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.CheckoutDraft;

public interface CheckoutDraftRepository extends JpaRepository<CheckoutDraft,String> {
    Optional<CheckoutDraft> findByTokenAndOwnerEmail(String token,String ownerEmail);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CheckoutDraft d where d.token=:token and d.ownerEmail=:owner")
    Optional<CheckoutDraft> locked(@Param("token") String token,@Param("owner") String owner);
    @Modifying @Query("delete from CheckoutDraft d where d.ownerEmail=:owner and d.orderId is null")
    void deletePending(@Param("owner") String owner);
    void deleteByExpiresAtLessThan(long timestamp);
}

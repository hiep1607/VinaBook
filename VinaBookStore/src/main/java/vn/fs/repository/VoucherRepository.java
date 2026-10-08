package vn.fs.repository;
import java.util.*;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.fs.entity.Voucher;
public interface VoucherRepository extends JpaRepository<Voucher,Long> {
 Optional<Voucher> findByCode(String code);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from Voucher v where v.code=:code") Optional<Voucher> lockByCode(@Param("code") String code);
 List<Voucher> findTop100ByOrderByIdDesc();
}

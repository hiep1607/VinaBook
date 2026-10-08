package vn.fs.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.VoucherUse;
public interface VoucherUseRepository extends JpaRepository<VoucherUse,Long> {
 long countByVoucherId(Long voucherId);long countByVoucherIdAndOwnerId(Long voucherId,Long ownerId);
}

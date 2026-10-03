package vn.fs.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.StorePolicy;
public interface StorePolicyRepository extends JpaRepository<StorePolicy,Long> {
    java.util.Optional<StorePolicy> findTopByPolicyKeyOrderByVersionDesc(String policyKey);
}

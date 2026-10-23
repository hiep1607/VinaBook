package vn.fs.automation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationRunRepository extends JpaRepository<AutomationRun,String> {
    List<AutomationRun> findTop20ByOrderByStartedAtDesc();
    long deleteByStartedAtBefore(java.time.Instant before);
}

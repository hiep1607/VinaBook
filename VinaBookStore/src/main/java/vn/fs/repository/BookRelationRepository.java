package vn.fs.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.BookRelation;

public interface BookRelationRepository extends JpaRepository<BookRelation, Long> {
    List<BookRelation> findBySourceProductId(Long sourceProductId);
    List<BookRelation> findByTargetProductId(Long targetProductId);
    void deleteBySourceProductId(Long sourceProductId);
}

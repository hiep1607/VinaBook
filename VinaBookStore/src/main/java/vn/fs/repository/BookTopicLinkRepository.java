package vn.fs.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.BookTopicLink;

public interface BookTopicLinkRepository extends JpaRepository<BookTopicLink, Long> {
    List<BookTopicLink> findByProductId(Long productId);
    List<BookTopicLink> findByTopicIdIn(Collection<Long> topicIds);
    void deleteByProductId(Long productId);
}

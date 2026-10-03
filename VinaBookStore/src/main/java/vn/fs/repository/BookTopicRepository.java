package vn.fs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.BookTopic;

public interface BookTopicRepository extends JpaRepository<BookTopic, Long> {
    Optional<BookTopic> findByNameIgnoreCase(String name);
}

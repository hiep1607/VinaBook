package vn.fs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.ChatAccountMemory;

public interface ChatAccountMemoryRepository extends JpaRepository<ChatAccountMemory, Long> {}

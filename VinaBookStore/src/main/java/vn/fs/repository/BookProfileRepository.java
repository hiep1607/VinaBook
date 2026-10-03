package vn.fs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.fs.entity.BookProfile;

public interface BookProfileRepository extends JpaRepository<BookProfile, Long> {
    long countByApprovedTrueAndEmbeddingJsonIsNull();
    java.util.List<BookProfile> findByApprovedTrueAndEmbeddingJsonIsNull(org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update BookProfile p set p.embeddingJson=:vector where p.productId=:id and p.approved=true and p.searchText=:text and p.embeddingJson is null")
    int storeEmbeddingIfCurrent(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("text") String text,
        @org.springframework.data.repository.query.Param("vector") String vector);
}

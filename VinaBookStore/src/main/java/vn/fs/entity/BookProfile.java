package vn.fs.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Approved editorial facts; current price, stock and ratings stay on their own records. */
@Entity @Table(name = "book_profiles")
@Getter @Setter @NoArgsConstructor
public class BookProfile {
    @Id @Column(name = "product_id") private Long productId;
    @Column(columnDefinition = "TEXT") private String summary;
    private String audience;
    @Column(name = "reading_level") private String readingLevel;
    @Column(name = "reading_style") private String readingStyle;
    @Column(name = "learning_goal") private String learningGoal;
    private String language;
    private String publisher;
    @Column(name = "publication_year") private Integer publicationYear;
    @Column(name = "page_count") private Integer pageCount;
    private String isbn;
    @Column(name = "source_note", columnDefinition = "TEXT") private String sourceNote;
    @Column(name = "search_text", columnDefinition = "TEXT") private String searchText;
    @Column(name = "embedding_json", columnDefinition = "LONGTEXT") private String embeddingJson;
    private boolean approved;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}

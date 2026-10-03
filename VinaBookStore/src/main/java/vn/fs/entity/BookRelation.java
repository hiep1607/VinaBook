package vn.fs.entity;

import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "book_relations", uniqueConstraints = @UniqueConstraint(columnNames = {"source_product_id", "target_product_id", "relation_type"}))
@Getter @Setter @NoArgsConstructor
public class BookRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_product_id", nullable = false) private Long sourceProductId;
    @Column(name = "target_product_id", nullable = false) private Long targetProductId;
    @Column(name = "relation_type", nullable = false, length = 32) private String relationType;
    @Column(length = 250) private String evidence;
}

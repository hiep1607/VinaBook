package vn.fs.entity;

import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "book_topic_links", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "topic_id"}))
@Getter @Setter @NoArgsConstructor
public class BookTopicLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "topic_id", nullable = false) private Long topicId;
    @Column(length = 250) private String evidence;
}

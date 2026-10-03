package vn.fs.entity;

import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "book_topics")
@Getter @Setter @NoArgsConstructor
public class BookTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(name = "parent_id") private Long parentId;
}

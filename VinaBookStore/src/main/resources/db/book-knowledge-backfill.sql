-- Safe to repeat after dev JPA creates the tables or after a restored catalog import.
-- Copies only catalog facts; it never overwrites an editor-approved profile.
INSERT INTO book_profiles(product_id, summary, search_text, source_note, approved, updated_at)
SELECT p.product_id, p.description,
       CONCAT_WS(' ', p.name, p.author, c.category_name, p.description),
       'Mô tả sản phẩm hiện có trong catalog', b'1', NOW()
FROM products p LEFT JOIN categories c ON c.category_id = p.category_id
WHERE NOT EXISTS (SELECT 1 FROM book_profiles existing WHERE existing.product_id = p.product_id);

INSERT IGNORE INTO book_topics(name)
SELECT DISTINCT c.category_name FROM categories c
WHERE c.category_name IS NOT NULL AND c.category_name <> '';

INSERT IGNORE INTO book_topic_links(product_id, topic_id, evidence)
SELECT p.product_id, t.id, 'Danh mục hiện có trong catalog'
FROM products p JOIN categories c ON c.category_id = p.category_id
JOIN book_topics t ON t.name = c.category_name;

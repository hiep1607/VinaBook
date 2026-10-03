-- Apply once before a production deployment (production uses ddl-auto=validate).
-- Existing product rows remain the source of price, stock, sales and visibility.
CREATE TABLE book_profiles (
  product_id BIGINT NOT NULL PRIMARY KEY,
  summary TEXT NULL,
  audience VARCHAR(255) NULL,
  reading_level VARCHAR(255) NULL,
  reading_style VARCHAR(255) NULL,
  learning_goal VARCHAR(255) NULL,
  language VARCHAR(255) NULL,
  publisher VARCHAR(255) NULL,
  publication_year INT NULL,
  page_count INT NULL,
  isbn VARCHAR(255) NULL,
  source_note TEXT NULL,
  search_text TEXT NULL,
  embedding_json LONGTEXT NULL,
  approved BIT(1) NOT NULL DEFAULT b'0',
  updated_at DATETIME NULL,
  CONSTRAINT fk_book_profile_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
CREATE TABLE book_topics (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  parent_id BIGINT NULL,
  CONSTRAINT fk_book_topic_parent FOREIGN KEY (parent_id) REFERENCES book_topics(id)
);
CREATE TABLE book_topic_links (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  evidence VARCHAR(250) NULL,
  UNIQUE KEY uk_book_topic (product_id, topic_id),
  CONSTRAINT fk_book_topic_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  CONSTRAINT fk_book_topic_topic FOREIGN KEY (topic_id) REFERENCES book_topics(id)
);
CREATE TABLE book_relations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_product_id BIGINT NOT NULL,
  target_product_id BIGINT NOT NULL,
  relation_type VARCHAR(32) NOT NULL,
  evidence VARCHAR(250) NULL,
  UNIQUE KEY uk_book_relation (source_product_id, target_product_id, relation_type),
  CONSTRAINT fk_book_relation_source FOREIGN KEY (source_product_id) REFERENCES products(product_id),
  CONSTRAINT fk_book_relation_target FOREIGN KEY (target_product_id) REFERENCES products(product_id)
);

-- Backfill only facts already present in the catalog; no invented metadata.
INSERT INTO book_profiles(product_id, summary, search_text, source_note, approved, updated_at)
SELECT p.product_id, p.description,
       CONCAT_WS(' ', p.name, p.author, c.category_name, p.description),
       'Mô tả sản phẩm hiện có trong catalog', b'1', NOW()
FROM products p LEFT JOIN categories c ON c.category_id = p.category_id;
INSERT IGNORE INTO book_topics(name)
SELECT DISTINCT c.category_name FROM categories c WHERE c.category_name IS NOT NULL AND c.category_name <> '';
INSERT IGNORE INTO book_topic_links(product_id, topic_id, evidence)
SELECT p.product_id, t.id, 'Danh mục hiện có trong catalog'
FROM products p JOIN categories c ON c.category_id = p.category_id
JOIN book_topics t ON t.name = c.category_name;

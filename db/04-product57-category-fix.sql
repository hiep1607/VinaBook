-- =====================================================================
-- VinaBook — Sửa thể loại sai cho sản phẩm «Đắc Nhân Tâm» (idempotent)
--
-- Hiện tượng: product #57 (Đắc Nhân Tâm — Dale Carnegie, sách kỹ năng/
--   tâm lý) đang thuộc category «Sách Truyện, tiểu thuyết», dẫn tới
--   breadcrumb và sách liên quan sai trên storefront.
--
-- Taxonomy chuẩn trong database.sql:
--   6 = 'Sách Truyện, tiểu thuyết'
--   7 = 'Sách Tâm lý, tâm linh, tôn giáo'   ← đúng chủ đề Đắc Nhân Tâm
--
-- LƯU Ý: bản seed `database.sql` chỉ chứa product_id 3..55, tức bản ghi
--   #57 chỉ tồn tại trong MySQL đang chạy. Chủ dự án đã duyệt sửa sau
--   kiểm thử chatbot ngày 2026-10-04; đã cập nhật bản ghi và search_text.
--
-- Idempotent: chạy lại nhiều lần không đổi thêm dữ liệu — chỉ UPDATE khi
--   category hiện tại chưa đúng; match theo TÊN sách (khóa nghiệp vụ),
--   không hard-code theo id để an toàn với môi trường có auto_increment khác.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE products p
JOIN categories wrong_cat ON wrong_cat.category_id = p.category_id
JOIN categories target ON target.category_name = 'Sách Tâm lý, tâm linh, tôn giáo'
SET p.category_id = target.category_id
WHERE LOWER(TRIM(p.name)) = 'đắc nhân tâm'
  AND p.author = 'Dale Carnegie'
  AND wrong_cat.category_name = 'Sách Truyện, tiểu thuyết';

-- Làm mới dữ liệu dẫn xuất; worker tạo lại embedding cho hồ sơ đã duyệt.
UPDATE book_profiles bp
JOIN products p ON p.product_id = bp.product_id
JOIN categories c ON c.category_id = p.category_id
SET bp.search_text = REPLACE(bp.search_text, 'Sách Truyện, tiểu thuyết', c.category_name),
    bp.embedding_json = NULL, bp.updated_at = NOW()
WHERE LOWER(TRIM(p.name)) = 'đắc nhân tâm'
  AND p.author = 'Dale Carnegie'
  AND c.category_name = 'Sách Tâm lý, tâm linh, tôn giáo'
  AND bp.search_text LIKE '%Sách Truyện, tiểu thuyết%';

COMMIT;

-- ---------------------------------------------------------------------
-- Verification (chạy lại sau UPDATE: số dòng thay đổi phải = 0)
-- ---------------------------------------------------------------------

-- Bản ghi mục tiêu sau khi sửa (kỳ vọng: category = Sách Tâm lý...):
SELECT p.product_id, p.name, p.category_id, c.category_name
FROM products p LEFT JOIN categories c ON c.category_id = p.category_id
WHERE LOWER(TRIM(p.name)) LIKE '%đắc nhân tâm%';

-- Phòng thủ: nếu taxonomy thiếu category đích thì script KHÔNG chạy được
-- đúng nghĩa — kiểm tra trước khi thực hiện:
SELECT category_id, category_name FROM categories
WHERE category_name = 'Sách Tâm lý, tâm linh, tôn giáo';

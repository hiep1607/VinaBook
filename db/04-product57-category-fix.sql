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
--   #57 chỉ tồn tại trong MySQL đang chạy. Script này phải được CHỦ DỰ ÁN
--   duyệt và chạy thủ công (xem blocker trong AI_WORKLOG.md / docs/bugs.md).
--
-- Idempotent: chạy lại nhiều lần không đổi thêm dữ liệu — chỉ UPDATE khi
--   category hiện tại chưa đúng; match theo TÊN sách (khóa nghiệp vụ),
--   không hard-code theo id để an toàn với môi trường có auto_increment khác.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE products p
JOIN categories wrong_cat ON wrong_cat.category_id = p.category_id
SET p.category_id = (
    SELECT c.category_id
    FROM categories c
    WHERE c.category_name = 'Sách Tâm lý, tâm linh, tôn giáo'
    LIMIT 1
)
WHERE LOWER(TRIM(p.name)) LIKE '%đắc nhân tâm%'
  AND wrong_cat.category_name <> 'Sách Tâm lý, tâm linh, tôn giáo';

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

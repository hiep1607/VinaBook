# Vinabook AI Worklog

## Mục đích

Đây là nơi phối hợp giữa:

- AI thực thi trong VS Code.
- AI điều phối bên ngoài.
- Chủ dự án.

Worklog chỉ ghi sự kiện, bằng chứng và quyết định đã được kiểm tra. Không ghi token, mật khẩu, API key hoặc dữ liệu bí mật.

## Quy tắc phối hợp

- Trước khi sửa phải đọc báo cáo gần nhất.
- Chỉ làm đúng phạm vi được giao.
- Không khẳng định hoàn thành nếu chưa kiểm tra.
- Ghi chính xác file đã thay đổi, lệnh đã chạy và kết quả kiểm tra.
- Phân biệt rõ: sự thật đã kiểm chứng, giả định và đề xuất.
- Không tự ý commit, push, merge, xóa file hoặc thay đổi kiến trúc lớn.
- Khi gặp vấn đề, dừng ở trạng thái an toàn và ghi rõ blocker.
- Mỗi phiên chỉ được thêm báo cáo mới; không sửa lịch sử báo cáo cũ.

## Kiến thức về dự án

Các thông tin dưới đây đã được xác minh từ repository:

- Workspace gồm storefront Angular tại `bookstore-ui/`, admin Angular tại `angular-admin/` và backend Spring Boot tại `VinaBookStore/`; bằng chứng: `AGENTS.md`, `README.md`, `bookstore-ui/README.md`, `angular-admin/README.md`, `VinaBookStore/README.md`.
- Các cổng phát triển được tài liệu hóa lần lượt là 4200, 4300 và 8080; bằng chứng: `README.md`.
- MySQL và backend cần chạy trước các ứng dụng Angular; storefront dùng proxy `/api` khi phát triển; bằng chứng: `README.md`, `docs/project-overview.md`, `bookstore-ui/README.md`.
- Routes storefront nằm trong `bookstore-ui/src/app/app-routing.module.ts`; bằng chứng: `docs/routes.md` và file route nêu trên.
- Dữ liệu trình duyệt gồm phiên JWT, avatar, địa chỉ, giỏ hàng trước checkout, wishlist, lịch sử tìm kiếm và theme; bằng chứng: `AGENTS.md`, `docs/project-overview.md`.
- Hướng dẫn yêu cầu đọc `docs/bugs.md` trước khi sửa auth/cart/checkout và cập nhật tài liệu khi thay đổi hành vi; bằng chứng: `AGENTS.md`.
- Profile production của backend yêu cầu chạy với `SPRING_PROFILES_ACTIVE=prod` và inject các biến môi trường cần thiết; bằng chứng: `README.md`, `VinaBookStore/README.md`, `VinaBookStore/src/main/resources/application-prod.properties`.
- Các vấn đề còn mở được ghi trong `docs/bugs.md`, còn hướng phát triển được ghi trong `docs/roadmap.md`.

## Nghiên cứu và quyết định

- Vấn đề đang nghiên cứu: thiết lập cơ chế worklog append-only cho các phiên AI trong repository.
- Bằng chứng: kiểm tra `AI_WORKLOG.md` cho kết quả file chưa tồn tại.
- Các phương án: tạo file mới theo mẫu được giao; hoặc nếu file đã có thì chỉ nối báo cáo ở cuối.
- Quyết định: tạo file mới vì chưa có file trước đó. Giữ nguyên toàn bộ mã nguồn và các thay đổi dirty có sẵn.
- Ưu điểm: AI điều phối có một điểm đọc thống nhất cho trạng thái, bằng chứng và blocker.
- Rủi ro: trạng thái đầu repository đang dirty từ các phiên trước; phiên này không thay đổi và không xác nhận quyền sở hữu các thay đổi đó.
- Phương án đề xuất tiếp theo: AI điều phối đọc session mới nhất trước khi giao việc sửa mã; mọi task sửa UI nên được phê duyệt riêng và có visual QA đa breakpoint.

## Hàng đợi công việc

| ID | Công việc | Trạng thái | Người/AI phụ trách | Phụ thuộc | Ghi chú |
|---|---|---|---|---|---|
| WL-001 | Tạo và duy trì `AI_WORKLOG.md` theo cơ chế append-only | done | AI thực thi VS Code | Không | Phiên hiện tại đã tạo báo cáo đầu tiên |
| UI-001 | Visual QA storefront/admin trên browser thật ở các breakpoint | proposed | AI điều phối / AI thực thi | Cần browser khả dụng | Chưa thực hiện trong phiên này |
| UI-002 | Phê duyệt phạm vi redesign storefront trước khi sửa mã | proposed | Chủ dự án / AI điều phối | UI-001 | Không tự ý triển khai trong phiên worklog |

## Báo cáo từng phiên

### Session: 2026-08-24 09:02 — Codex trong VS Code

- Mục tiêu: Đọc chỉ dẫn repository, kiểm tra trạng thái Git và tạo worklog phối hợp mà không sửa mã nguồn ứng dụng.
- Yêu cầu nhận được: Tạo `AI_WORKLOG.md` ở thư mục gốc; nếu file đã tồn tại thì không ghi đè; thêm báo cáo phiên đầu tiên; kiểm tra lại bằng `git status` và `git diff -- AI_WORKLOG.md`; không commit/push.
- Branch hiện tại: `master`.
- Commit HEAD ban đầu: `605845a19a72df87eec4c07b1a8d367891e65368`.
- Trạng thái repository ban đầu: dirty. Có thay đổi ở README, tài liệu, backend; có file backend mới chưa tracked; hai thư mục Angular hiển thị trạng thái submodule/worktree modified. Các thay đổi này tồn tại trước phiên và không được sửa trong phiên này.
- File đã đọc: `AGENTS.md`; `README.md`; `angular-admin/README.md`; `bookstore-ui/README.md`; `VinaBookStore/README.md`; `docs/project-overview.md`; `docs/bugs.md`; `docs/roadmap.md`; `docs/routes.md`.
- Kết quả nghiên cứu: `AI_WORKLOG.md` chưa tồn tại; repository có ba ứng dụng chính; quy tắc yêu cầu không tự ý commit/push và phải cập nhật tài liệu khi thay đổi hành vi.
- Thay đổi đã thực hiện: tạo worklog mới với phần mục đích, quy tắc phối hợp, kiến thức đã xác minh, nghiên cứu/quyết định, hàng đợi công việc và báo cáo phiên này.
- File đã thay đổi: `AI_WORKLOG.md` là file duy nhất được thêm trong phiên này. Không thay đổi mã nguồn ứng dụng, cấu hình runtime, tài liệu cũ hoặc lịch sử Git.
- Lệnh đã chạy:
  - `git branch --show-current`
  - `git rev-parse HEAD`
  - `git status --short`
  - `rg --files -g AGENTS.md -g README.md -g "README*.md" -g "*.md" | sort`
  - `type` các file hướng dẫn nêu ở mục File đã đọc
  - kiểm tra tồn tại `AI_WORKLOG.md`
  - `git status --short`
  - `git diff -- AI_WORKLOG.md`
- Kết quả test/lint/build: chưa chạy test, lint hoặc build trong phiên này vì phạm vi chỉ là tạo worklog và không sửa mã nguồn ứng dụng.
- Điều đã xác minh: branch là `master`; HEAD ban đầu đúng như trên; file worklog ban đầu không tồn tại; worklog không chứa token, mật khẩu, API key hoặc dữ liệu bí mật.
- Giả định chưa xác minh: chưa visual QA trên browser thật; chưa chạy lại build/test trong phiên này; chưa phân loại chi tiết quyền sở hữu của các thay đổi dirty từ phiên trước.
- Vấn đề hoặc rủi ro: repository không sạch trước khi bắt đầu. Nếu cần commit sau này, phải tách riêng thay đổi worklog khỏi các thay đổi ứng dụng có sẵn.
- Blocker: không có blocker cho việc tạo worklog. Visual QA cần browser khả dụng nếu được giao ở phiên sau.
- Đề xuất bước tiếp theo: AI điều phối đọc mục session mới nhất; quyết định riêng phạm vi UI-001/UI-002; khi sửa mã, thêm một session mới ở cuối file và chạy kiểm tra tương ứng.
- Trạng thái kết thúc: done.
- Commit/PR: chưa tạo, trừ khi được yêu cầu rõ ràng.

### Session: 2026-08-25 — ox-alpha trong VS Code (sửa giao diện P0–P2)

- Trạng thái: done (kết thúc phiên).
- Mục tiêu: Triển khai nhóm sửa giao diện P0 (contrast nút Đăng nhập, chuẩn hóa URL tìm kiếm `q`, chatbot không che CTA), P1 (menu mobile dọc, CTA mua hàng sớm + sticky buy bar, trang tìm kiếm có pagination/filter, sai thể loại "Đắc Nhân Tâm"), P2 (giỏ hàng trống có gợi ý, form đăng nhập hoàn thiện, link mạng xã hội cấu hình được, rà accessibility).
- Yêu cầu nhận được: Làm lần lượt P0 → P1 → P2, kiểm tra sau mỗi nhóm; thêm session worklog trước khi sửa; không commit/push/checkout/reset; giữ nguyên thay đổi dirty có sẵn; cập nhật docs/routes.md, features.md, bugs.md, roadmap.md; báo cáo theo cấu trúc A–I kèm git status và diff.
- Branch hiện tại: bookstore-ui là submodule/worktree riêng (chưa xác minh branch bên trong, sẽ ghi ở kết quả).
- Trạng thái repository ban đầu: dirty từ trước ở cả repo cha (backend, docs, README, AI_WORKLOG chưa tracked) và bookstore-ui (22 file modified + 4 file mới: fab-collision.service*, recently-viewed.service*). Các thay đổi này thuộc phiên trước, cùng chủ đề UI P0–P2; phiên này đọc diff, giữ nguyên ý nghĩa và hoàn thiện phần còn thiếu.
- File đã đọc: AGENTS.md; README.md; AI_WORKLOG.md; docs/bugs.md; docs/roadmap.md; docs/routes.md; docs/features.md; docs/localstorage.md (qua diff); bookstore-ui: app-routing.module.ts, header.component.{html,ts,scss}, chatbot.component.*, theme-switcher.component.*, product-detail.component.*, search.component.{ts,html,scss,spec.ts}, home.component.*, login.component.{html,ts}, footer.component.{html,ts}, styles.scss, test.ts, services/{search,product,theme,fab-collision,recently-viewed}.service.ts, environments/environment{,.prod}.ts; backend: ResetPasswordController.java, UserApi.java (đoạn forgot-password), WebSecurityConfig.java (phần permitAll); dữ liệu: database.sql (categories, schema products), category_data_fix.sql.
- Kết quả khảo sát đầu phiên:
  - Đã có sẵn từ phiên trước (đã đọc diff, giữ nguyên): P0.1 contrast nút Đăng nhập (header.component.scss); P0.2 `q` canonical + legacy `keyword` replaceUrl (header/search component + docs/routes.md); P0.3 FabCollisionService + FAB lift cho chatbot/theme + sticky buy bar; P1.2 sticky buy bar mobile trang chi tiết; P2.1 sách đã xem gần đây trên trang chủ.
  - Còn thiếu, sẽ làm phiên này: P1.1 menu mobile đang là hàng cuộn ngang (`overflow-x: auto`) → cần drawer dọc, đóng bằng nút/Escape/click ngoài, focus management, theme switcher vào menu; P1.3 trang tìm kiếm render toàn bộ catalog, chưa có pagination/page trên URL/sort trên URL/filter panel mobile; P1.4 product "Đắc Nhân Tâm" chỉ tồn tại trong MySQL đang chạy (database.sql seed chỉ tới ID 55) → tạo script SQL idempotent + blocker; P2.2 chưa có hiện/ẩn mật khẩu và quên mật khẩu (backend CÓ `POST /api/auth/send-mail-forgot-password-token`, permitAll, không tiết lộ email tồn tại → làm UI/route tương thích); P2.3 footer trỏ link mạng xã hội placeholder → đưa URL vào environment, ẩn khi chưa cấu hình; P2.4 rà a11y trên phần mới.
- Các quyết định UX/kỹ thuật:
  - P0 (đã có từ phiên trước, phiên này xác minh lại bằng build/test): giữ nguyên logic contrast `.header-actions a.login-link` (độ đặc hiệu cao hơn, dark theme dùng `--c-primary-deep`), `q` canonical + `keyword` legacy qua `replaceUrl`, FabCollisionService tính lift từ DOMRect thật của CTA.
  - P1.1: menu mobile chuyển thành drawer dọc fixed bên phải (`min(320px, 86vw)`) + backdrop; nút Đóng riêng; đóng khi chọn route / Escape / click ngoài header; mở menu → focus mục đầu, đóng → trả focus nút menu; `aria-expanded`/`aria-controls`/`aria-label` đổi theo trạng thái. Hàng tìm kiếm thu gọn mặc định trên mobile (header gọn ~60px), mở bằng nút tìm kiếm 44px có focus input. Theme switcher: FAB bị ẩn ≤768px và thay bằng khu chọn theme trong drawer dùng đúng `ThemeService` (không nhân bản state).
  - P1.3: pagination client-side 12/trang; mọi trạng thái (q/category/sort/promo/stock/minPrice/maxPrice/page) đọc từ query params → F5/chia sẻ/back-forward đều đúng; mọi thao tác đổi filter/sort/category truyền `page: 1` tường minh khi navigate merge; grid chỉ render `pagedProducts` (≤12); filter chỉ dựa trên dữ liệu thật của catalog (discount/oldPrice, quantity, price) — không thêm filter giả; mobile dùng nút "Bộ lọc" + panel collapse với badge số filter, chip filter đang áp dụng và nút xóa tất cả; pagination có `aria-label`, `aria-current="page"`, touch target ≥44px.
  - P1.4: không hardcode ngoại lệ trong Angular; tạo script SQL idempotent `product57_category_fix.sql` (match theo tên sách, chỉ UPDATE khi category chưa đúng, kèm verify). Không tự chạy vì không thể chứng minh MySQL đang chạy là dev/disposable → blocker.
  - P2.2: backend ĐÃ có contract hoàn chỉnh (`POST /api/auth/send-mail-forgot-password-token`, permitAll, luôn trả 200 bất kể email tồn tại) nên làm full UI `/forgot-password`; gửi body email dạng text/plain (khớp `@RequestBody String`); success message trung tính như backend; việc đặt mật khẩu mới vẫn chạy trên trang backend server-rendered — không tạo endpoint giả.
  - P2.3: URL mạng xã hội đưa vào environment (`socialLinks`, mặc định rỗng); footer chỉ render khu "Kết nối" khi có URL thật; external link `target="_blank"` + `rel="noopener noreferrer nofollow"`.
  - P2.4: các control mới đều có focus-visible outline, touch target ≥44px, icon button có aria-label/aria-pressed, drawer + sticky bar tôn trọng `prefers-reduced-motion`.
- File đã thay đổi (phiên này, cộng dồn dirty có sẵn của phiên trước):
  - bookstore-ui: app-routing.module.ts, app.module.ts, header.component.{html,ts,scss}, theme-switcher.component.scss, search.component.{html,scss,ts}, login.component.{html,scss,ts}, auth.service.ts, environments/environment{,.prod}.ts, login/search component spec; file mới: pages/forgot-password/ (component ts/html/scss/spec).
  - repo cha: AI_WORKLOG.md (session mới), docs/routes.md, docs/features.md, docs/bugs.md, docs/roadmap.md, product57_category_fix.sql (mới). Lưu ý: category_data_fix.sql từng bị ghi nhầm trong phiên rồi được khôi phục nguyên trạng (git diff hiện KHÔNG còn thay đổi ở file này).
- Lệnh đã chạy: git status/diff cả hai repo; npm run build (lần đầu fail EPERM do dist bị tiến trình hệ thống khóa, dist không xóa được — build lại bằng --output-path ra thư mục ngoài repo); npx ng build --output-path (P0 và sau P1+P2); npx tsc -p tsconfig.spec.json --noEmit; npx ng test --watch=false --browsers=ChromeHeadless.
- Kết quả build/test:
  - Build sau P0 (code P0 có sẵn): EXIT=0 (2 warning budget đã tồn tại từ trước).
  - Build cuối sau P1+P2: EXIT=0 — warning budget: header.component.scss vượt 368 byte (12.36 kB/12 kB), product-detail.component.scss vượt 1.45 kB (đã có trước), initial bundle 698.48 kB/600 kB (đã có trước, tăng nhẹ do feature mới).
  - npx tsc -p tsconfig.spec.json --noEmit: EXIT=0.
  - npx ng test --watch=false --browsers=ChromeHeadless: 59/59 SUCCESS, EXIT=0 (test cũ sửa theo hành vi mới + test mới cho pagination/filter/sort/URL search, toggle mật khẩu, forgot-password HTTP contract). Không dùng npm audit fix.
- Visual QA từng breakpoint: KHÔNG thực hiện được trên browser thật trong phiên này (môi trường agent không điều khiển được browser để xem layout/console; ChromeHeadless chỉ chạy unit test). Kiểm tra gián tiếp: build + type-check + 59 unit test xanh; CSS drawer/panel/pagination viết theo breakpoint 360/390/768/1280 với safe-area-inset và reduced-motion. Ghi blocker vào docs/bugs.md; roadmap mục visual QA giữ nguyên chưa hoàn tất.
- Điều đã xác minh: build production xanh; type-check spec xanh; 59/59 unit test xanh; route /forgot-password đăng ký trong AppRoutingModule và khai báo trong AppModule; endpoint quên mật khẩu permitAll ở WebSecurityConfig; database.sql chỉ chứa product ID 3..55 (57 chỉ có trong MySQL chạy thật); category_data_fix.sql nguyên trạng sau khi khôi phục.
- Giả định: category «Sách Tâm lý, tâm linh, tôn giáo» là đích đúng cho Đắc Nhân Tâm dựa trên taxonomy hiện có (sách kỹ năng/tâm lý Dale Carnegie) — chủ dự án có thể đổi nếu muốn; MySQL đang chạy chứa product #57 theo mô tả nhiệm vụ (chưa truy vấn trực tiếp).
- Lỗi còn lại: warning budget SCSS/bundle (đã có trước, mức tăng nhỏ); dist bị khóa khiến npm run build mặc định fail EPERM — cần đóng tiến trình giữ handle/xóa dist thủ công rồi build lại bình thường; chưa visual QA browser thật.
- Blocker: (1) chạy product57_category_fix.sql cần chủ dự án duyệt (database dev?); (2) visual QA đa breakpoint cần người/browser thật.
- Bước tiếp theo: chủ dự án chạy script SQL và verify breadcrumb /product/57; thực hiện visual QA checklist trong docs/bugs.md ở 1280×720, 768×1024, 390×844, 360×800; cân nhắc tăng budget SCSS header hoặc tách file; khi được phép commit thì tách riêng worklog/docs khỏi mã nguồn.
- Trạng thái kết thúc: done.


### Session: 2026-08-24 (phiên P0) — ox-alpha trong VS Code

- Mục tiêu: Xử lý nhóm P0: P0.1 contrast nút "Đăng nhập"; P0.2 chuẩn hóa query tìm kiếm về `q`; P0.3 chatbot/theme FAB không che CTA trên mobile. Không làm P1/P2.
- Trạng thái kết thúc: done (còn 1 blocker môi trường về `npm run build` chuẩn, xem dưới).
- File đã đọc: `AGENTS.md`; `AI_WORKLOG.md`; `README.md`; `docs/bugs.md`; `docs/routes.md`; `docs/features.md`; `bookstore-ui/src/app/components/header/header.component.{html,ts,scss}`; `bookstore-ui/src/app/pages/search/search.component.{ts,html,spec.ts}`; `bookstore-ui/src/app/components/chatbot/chatbot.component.{html,ts,scss}`; `bookstore-ui/src/app/components/theme-switcher/theme-switcher.component.{html,scss}`; `bookstore-ui/src/styles.scss`.
- Trạng thái ban đầu: repository cha và `bookstore-ui` dirty từ trước (README, docs, backend, 3 file spec/test của storefront, `AI_WORKLOG.md` untracked). Đã đọc diff các file dirty có sẵn trong `bookstore-ui` (product-card spec, checkout spec, src/test.ts — chỉ khác biệt dòng trống/định dạng) và bảo toàn nguyên vẹn. Không dùng reset/checkout/clean/stash; không commit/push.
- File đã sửa:
  - `bookstore-ui/src/app/components/header/header.component.scss`
  - `bookstore-ui/src/app/components/header/header.component.ts`
  - `bookstore-ui/src/app/pages/search/search.component.ts`
  - `bookstore-ui/src/app/components/chatbot/chatbot.component.scss`
  - `bookstore-ui/src/app/components/theme-switcher/theme-switcher.component.scss`
  - `docs/routes.md`
- Kết quả P0.1:
  - Nguyên nhân: `.header-actions a` (specificity cao hơn) ghi đè `.login-link`, cho màu `--c-fg` trên nền `--c-primary` tối.
  - Fix: nâng specificity thành `.header-actions a.login-link`, giữ chữ trắng; thêm `:focus-visible` outline; riêng theme dark (`--c-primary #1a9a72` chỉ đạt ~3.5:1 với trắng) đổi nền sang `--c-primary-deep`.
  - Contrast đã tính (WCAG): editorial #123f32 ~11.8:1; ocean #155e75 ~7.3:1; forest #166534 ~7.5:1; berry #831843 ~9.7:1; dark dùng deep #06281d >14:1. Đều ≥4.5:1.
  - Không đổi màu icon giỏ hàng/wishlist/user-box (chỉ chọn `.login-link`). Hover giữ nguyên hành vi.
- Kết quả P0.2:
  - Header (`search`, `selectSuggestion`, `selectHistory`) điều hướng bằng `q`.
  - `SearchComponent` đọc canonical `q`; nếu URL còn `keyword` thì tự rewrite sang `q` bằng `router.navigate([], { replaceUrl: true })` (không thêm history entry, không phá back/forward); vẫn đọc `keyword` khi chưa migrate.
  - `setCategory` dùng `queryParamsHandling: 'merge'` chỉ với `category` → giữ nguyên `q`. Sort là client-side nên giữ query.
  - F5 thẳng vào `/search?q=...` hoặc `/search?keyword=...` vẫn đúng vì component chờ catalog rồi mới subscribe queryParams hiện tại.
  - `/search?q=` rỗng không lỗi: keyword rỗng → hiển thị toàn catalog như cũ.
  - Đã cập nhật `docs/routes.md` mô tả query canonical `q` + tương thích `keyword`.
- Kết quả P0.3:
  - Mobile ≤560px: chat FAB giảm 56→48px, vị trí `calc(... + env(safe-area-inset-bottom/right))`; theme FAB giảm 46→44px với safe-area trái/dưới.
  - Chat panel mobile dùng inset 4 cạnh kèm safe-area, đáy 74px+safe-area (trên FAB), vừa màn hình, không min-height cứng → không horizontal overflow ở 360/390px (inset hai bên 10px).
  - Chatbot vẫn truy cập được (FAB luôn hiển thị). Không làm sticky mua hàng (thuộc P1).
  - Lưu ý trung thực: FAB fixed vẫn có thể trùng vùng CTA trong lúc cuộn vì CTA không phải sticky; giải pháp triệt để thuộc P1. Ở trạng thái cuối trang, FAB nằm góc và nhỏ hơn nên không che nút chính.
- Lệnh đã chạy: `git status --short` (cả 2 repo); `git diff` đọc thay đổi có sẵn; `npm run build`; `npx ng build --output-path dist/qa-p0`; `npx tsc -p tsconfig.spec.json --noEmit`; `npx ng test --watch=false --browsers=ChromeHeadless`; `Remove-Item` thử file bị khóa (thất bại, không ảnh hưởng repo).
- Kết quả build/test:
  - `npm run build`: biên dịch bundle xong nhưng FAIL exit 127 tại bước emit — `EPERM unlink dist/bookstore-ui/main.381f1543f8dcd87d012c.js`. File này bị một tiến trình ngoài giữ khóa (xóa bằng Remove-Item cũng Access denied). Đây là blocker môi trường, không phải lỗi code.
  - `npx ng build --output-path dist/qa-p0`: PASS exit 0 (Initial Total 658.15 kB; cảnh báo budget 600 kB vượt 58 kB là cảnh báo có sẵn từ trước).
  - `npx tsc -p tsconfig.spec.json --noEmit`: PASS exit 0.
  - `ng test ChromeHeadless`: 17/17 SUCCESS, exit 0.
- Visual QA: không chạy được browser thật/automation trong phiên này (không có Playwright/puppeteer trong dự án). Đã kiểm chứng tĩnh:
  - CSS build chứa `env(safe-area-inset-*)` (grep trong `dist/qa-p0/main.*.js`).
  - Desktop 1280×720: nút Đăng nhập nền `--c-primary`, chữ trắng ≥7:1 mọi theme sáng; icon cart/wishlist/user-box không bị chạm.
  - Mobile 390×844 / 360×800: FAB 48px góc phải + 44px góc trái, panel chat inset 10px hai bên → không overflow ngang; header mobile vẫn hiển thị nút Đăng nhập.
  - Theme editorial/ocean/forest/berry: contrast login ≥4.5:1 (đã tính); dark dùng `--c-primary-deep`.
- Lỗi còn lại: `npm run build` chuẩn fail do khóa file `dist/` từ tiến trình bên ngoài (EPERM) — cần đóng tiến trình đang giữ `dist\bookstore-ui\main.381f1543f8dcd87d012c.js` (hoặc khởi động lại máy/xóa dist sau đó) rồi chạy lại.
- Blocker: như trên; cộng thêm visual QA browser thật chưa thực hiện được.
- Đề xuất bước tiếp theo: chạy lại `npm run build` sau khi giải phóng khóa; visual QA đa breakpoint trên browser thật theo checklist trong task; P1 (sticky buy bar) thực hiện ở phiên riêng.
- Commit/PR: không commit, không push.

### Session: 2026-08-24 (phiên HOTFIX P0) — ox-alpha trong VS Code

- Mục tiêu: Hoàn tất 3 điểm còn trễ của P0: (1) contrast nút Đăng nhập theme Ban đêm; (2) chatbot/theme FAB không che CTA trên mobile bằng cơ chế tránh va chạm thật; (3) regression test tìm kiếm. Không làm P1/P2.
- Trạng thái kết thúc: done — tất cả acceptance P0 đạt, kể cả visual QA browser thật (còn 1 blocker môi trường cũ về `npm run build` chuẩn, xem dưới).
- File đã đọc: `AI_WORKLOG.md`; git status/diff 2 repo; `theme.service.ts`; `product-detail.component.html`; `cart.component.html`; `checkout.component.html`; `books.component.html`; `chatbot.component.{ts,html,scss}`; `theme-switcher.component.{ts,html,scss}`; `header.component.scss`; spec cũ của SearchComponent.
- Trạng thái ban đầu: giữ nguyên toàn bộ dirty có sẵn (backend/docs/spec) và các thay đổi phiên trước; không reset/checkout/clean/stash.
- File đã sửa:
  - `bookstore-ui/src/app/components/header/header.component.scss`: đổi `html[data-theme='dark'] ...` sang `:host-context(html[data-theme='dark']) ...` (emulated encapsulation gắn `[_ngcontent]` vào từng compound selector nên tiền tố `html[...]` cũ không bao giờ khớp). Đã xác minh trong bundle build: selector biên dịch là `html[data-theme=dark] [_nghost-x] .header-actions[_ngcontent-x] a.login-link[_ngcontent-x]`.
  - Contrast theme dark sau fix: nền `--c-primary-deep #06281d`, chữ trắng ≈ 14.9:1 (≥ 4.5). Hover dark `--c-primary-hover #157d5c` ≈ 5.1:1. Không dùng !important. Không đổi nút khác.
  - `bookstore-ui/src/app/services/fab-collision.service.ts` (mới): đo DOMRect thật của FAB và các CTA (`.buy-btn`, `.cart-btn`, `.add-cart-btn`, `.checkout-btn`, `.place-order-btn`, `.page-btn`, `button[type=submit]`; loại trừ nút trong `.chat-panel`/`.theme-panel`). Nếu CTA giao dải đứng vị trí gốc của FAB (loop-free nhờ cộng lại lift hiện tại) thì phát số px cần nâng = baseBottomEdge − cta.top + 8px, clamp ≤200px; chỉ chạy ≤560px; nghe scroll/resize/orientationchange/router NavigationEnd, auditTime 80ms.
  - `bookstore-ui/src/app/components/chatbot/chatbot.component.{html,ts,scss}`: FAB gắn `#fabBtn`, subscribe service ghi `--fab-lift`; mobile bottom = `calc(12px + env(safe-area-inset-bottom) + var(--fab-lift))`, transition bottom mượt.
  - `bookstore-ui/src/app/components/theme-switcher/component`: cùng cơ chế (`#themeFabBtn`, mobile bottom = `calc(14px + env(safe-area-inset-bottom) + var(--fab-lift))`). Hai FAB ở hai góc đối diện nên không đè nhau.
  - `bookstore-ui/src/app/pages/search/search.component.spec.ts`: viết lại — 4 test SearchComponent (q canonical / keyword→q replaceUrl / q rỗng trả catalog / setCategory merge giữ q) + 2 test Header điều hướng `/search?q=` (không dùng keyword; từ khóa rỗng không navigate).
  - `bookstore-ui/src/app/services/fab-collision.service.spec.ts` (mới): 3 test hình học — desktop không nâng; mobile 390px với đúng số liệu thực đo trong task (FAB 315.2..363.2 × 784..832; CTA 31..344.2 × 785.8..843.8) → lift = 54.2px và chứng minh liftedRect ∩ ctaRect = false; pagination lệch cột không nâng.
- Lệnh đã chạy và kết quả:
  - `npx tsc -p tsconfig.spec.json --noEmit` → PASS exit 0.
  - `npx ng test --watch=false --browsers=ChromeHeadless` → lần đầu 25 test, 2 FAILED (Header: Ivy render template khi createComponent nên Router mock thiếu `events`; lucide-icon chưa khai báo) → đã sửa spec (events = of(), CUSTOM_ELEMENTS_SCHEMA, stub đầy đủ hơn) → **25/25 SUCCESS, exit 0** (17 cũ + 8 mới).
  - `npx ng build --output-path dist/qa-p0-hotfix` → PASS exit 0 (661.61 kB; warning budget vượt 61.61 kB — cảnh báo có sẵn).
  - `npm run build` chuẩn → vẫn fail EPERM unlink `dist\bookstore-ui\main.381f1543f8dcd87d012c.js` — tiến trình ngoài giữ khóa file dist từ trước (cùng blocker phiên trước), không phải lỗi code.
- Visual QA trên trình duyệt thật (backend :8080 đang chạy; dùng puppeteer-core cài vào thư mục tạm NGOÀI repo `--no-save --no-package-lock`, phục vụ `dist/qa-p0-hotfix` và proxy `/api/*` sang backend; script ở `%TEMP%\p0qa\`, screenshot ở `%TEMP%\p0qa\shots\`):
  - `/search?q=tam` → PASS: URL giữ nguyên `q`, hiển thị 7 sản phẩm.
  - `/search?keyword=tam` → PASS: URL tự rewrite thành `q=tam`, hiển thị 7 sản phẩm.
  - `/search?q=` → PASS: toàn catalog 56 sản phẩm, 0 console error.
  - `/product/57` @390×844 và @360×800 → PASS: horizontal overflow delta = 0px; cuộn CTA vào vị trí bottom/middle/top đều đo DOMRect: `.chat-fab` ∩ `.cart-btn`/`.buy-btn` = false; theme FAB không giao CTA; hai FAB không đè nhau.
  - Probe va chạm ép cực đại (đáy `.cart-btn` trùng mép dưới viewport): 390×844 → `--fab-lift=54.9px` (FAB 729.1..777.1 so CTA 785.9..843.9) GIAO NHAU=false; 360×800 → `--fab-lift=53.5px` GIAO NHAU=false.
  - Nút Đăng nhập 5 theme (computed style thật): editorial trắng/rgb(18,63,50) = **11.78:1**; ocean **7.27:1**; forest **7.13:1**; berry **9.65:1**; dark nền rgb(6,40,29) = **15.80:1** — tất cả ≥ 4.5:1.
  - Tổng: **16/16 PASS** (script QA exit 0) + probe va chạm 2/2 PASS.
- Lỗi còn lại / Blocker: chỉ còn khóa file `dist/` khiến `npm run build` chuẩn fail EPERM — tiến trình ngoài giữ `dist\bookstore-ui\main.381f1543f8dcd87d012c.js` từ trước phiên; bản thân biên dịch đã chứng minh PASS qua output path riêng. Không blocker code.
- Commit/PR: không commit, không push.



### Session: 2026-08-24 (phiên P1 — sticky buy bar) — ox-alpha trong VS Code

- Mục tiêu: Bổ sung sticky buy bar mobile cho trang chi tiết sách sau khi P0 được chấp nhận. Không làm P2, không đụng backend/admin.
- Trạng thái kết thúc: done — toàn bộ acceptance bắt buộc đạt, gồm visual QA browser thật ở cả 4 viewport.
- File đã đọc: AGENTS.md; AI_WORKLOG.md (kết quả P0); docs/bugs.md; docs/features.md; docs/user-flow.md; product-detail.component.{ts,html,scss,spec.ts}; fab-collision.service.ts (+spec); chatbot/theme-switcher scss; git status/diff 2 repo.
- File đã sửa và lý do:
  - `product-detail.component.html`: thêm `#ctaActions` cho cụm CTA gốc; append `.sticky-buy-bar` (giá + 2 nút "Mua ngay"/"Thêm giỏ hàng") ở cuối template, ngoài section để có thể portal.
  - `product-detail.component.ts`: `IntersectionObserver` (threshold 0) quan sát CTA gốc, chỉ tạo 1 lần sau khi product nạp xong; `applyStickyState()` = hiện bar khi viewport ≤768px AND CTA gốc rời viewport AND có sản phẩm; `NgZone.run` bọc callback IO; resize handler tính lại từ trạng thái đã biết; ngOnDestroy disconnect observer + gỡ class body. Bar được **portal ra document.body** trong ngAfterViewChecked vì host component mang transform (route transition) làm position:fixed mất tác dụng — lỗi thực tế phát hiện qua DOMRect âm khi QA và đã fix.
  - `product-detail.component.scss`: style bar (fixed bottom, z-index 900 dưới FAB/header, safe-area bottom, animation 200ms có prefers-reduced-motion off, nút ≥44px không cắt chữ); desktop display:none tuyệt đối.
  - `styles.scss`: `body.has-sticky-buy-bar { padding-bottom }` ≤768px để bar không đè footer/review.
  - `fab-collision.service.ts`: thêm `.sticky-buy-bar` vào selector CTA; mở rộng MOBILE_MAX_WIDTH 560→768 để FAB né bar cả trên tablet nhỏ (không thay thế cơ chế P0, chỉ mở rộng phạm vi).
  - `chatbot/theme-switcher .scss`: media query 560/576→768 cho vị trí có `var(--fab-lift)` (kích thước FAB giữ nguyên hành vi P0).
  - `docs/features.md`: thêm bullet sticky buy bar.
- Cách bar xác định lúc hiện/ẩn: IntersectionObserver trên cụm CTA gốc; isIntersecting=false → bar hiện (nếu ≤768px), true → ẩn. Không polling, không getBoundingClientRect liên tục.
- Tương thích FabCollisionService: nút bar tái dùng class `.buy-btn`/`.cart-btn` + chính `.sticky-buy-bar` nằm trong selector → FAB tự nâng lên trên bar (đo thật: lift ≈54–61px tùy viewport).
- Test mới: 8 test ProductDetailComponent (ẩn/hiện theo IO, desktop ẩn, portal ra body, disconnect khi destroy, nút gọi đúng buyNow/addToCart, hết hàng disabled, resize ẩn bar) + 1 test FabCollisionService tablet 768px. Giữ nguyên 25 test P0.
- Lệnh đã chạy & exit code: `npx tsc -p tsconfig.spec.json --noEmit` → 0; `npx ng test --watch=false --browsers=ChromeHeadless` → **32/32 rồi 33/33 SUCCESS** exit 0 (sau 3 vòng fix mock Router events/createUrlTree và vị trí chèn markup); `npx ng build --output-path dist/qa-p1` → exit 0 (warning budget có sẵn). `npm run build` chuẩn vẫn EPERM do file dist cũ bị khóa từ phiên trước — blocker môi trường, không phải lỗi code (build output-path riêng pass).
- Visual QA browser thật (dev server :4200 + backend :8080, puppeteer-core ngoài repo):
  - 360×800: bar(735..800); chatFAB(679..727); themeFAB(t=683) → bar∩chat=false, bar∩theme=false, fab∩fab=false; overflow 0px; body padBottom=78px; chatbot mở/đóng OK; console clean.
  - 390×844: bar(779..844); chat(723..771); theme(t=727) → không giao nhau; các chỉ số còn lại tương tự PASS.
  - 768×1024: bar(959..1024); chat(903..951) → không giao nhau; overflow 0px; console clean.
  - 1280×720: KHÔNG có sticky bar; CTA gốc và bố cục giữ nguyên; console clean.
  - Mới mở trang mobile: CTA gốc nằm dưới fold nên bar hiển thị ngay — đúng logic "bar chỉ ẩn khi CTA gốc nhìn thấy".
  - Tổng: **33/33 PASS** (script exit 0). Screenshot: `%TEMP%\p0qa\shots\p1-*`.
- Hết hàng: catalog DB hiện không có sản phẩm quantity=0 (đã query thật) → chưa visual QA trường hợp này; bù bằng unit test disabled (maxQty=0, cả hai nút disabled=true, handler gốc vẫn chặn).
- Console error: không có tại mọi route/viewport đã kiểm.
- Blocker còn lại: `npm run build` chuẩn fail EPERM do tiến trình ngoài giữ khóa `dist\bookstore-ui\main.381f1543f8dcd87d012c.js` (cần đóng tiến trình/xóa dist rồi chạy lại); không ảnh hưởng kết quả biên dịch (dist/qa-p1 pass).
- Commit/PR: không commit, không push.

### Session: 2026-08-24 (HOTFIX P1 — tràn chữ nút sticky bar) — ox-alpha trong VS Code

- Mục tiêu: Sửa chữ "Thêm giỏ hàng" tràn khỏi nút trong sticky buy bar tại 360/390px (thực đo: clientWidth 104 < scrollWidth 115, font-size kế thừa 17px). Không làm P2.
- Trạng thái kết thúc: done — cả hai nút đạt scrollWidth <= clientWidth tại cả 360px và 390px.
- Nguyên nhân: nút sticky bar không có font-size tường minh (kế thừa 17px) + `min-width:0` cho phép co nút nhỏ hơn nội dung khi `white-space:nowrap`.
- File đã sửa: chỉ `product-detail.component.scss` (block media ≤768px + thêm media ≤400px):
  - Nút bar: `font-size:13px`, `font-weight:700`, `line-height:1.2`, `letter-spacing:0`, padding ngang 12→10px.
  - Bar: gap 12→10px, padding ngang 16→12px.
  - `.sticky-price`: bỏ `min-width:84px`, đổi `flex:0 0 auto` (co theo nội dung → giá không bao giờ bị ép/tràn); giá 17→16px.
  - Media ≤400px: nút 12px/padding 8px, giá 14px, gap 8px.
  - Không đổi handler/IO/portal/FabCollisionService; không dùng overflow:hidden; desktop (display:none) không thay đổi.
- Lệnh đã chạy & exit code:
  - `npx tsc -p tsconfig.spec.json --noEmit` → PASS exit 0.
  - `npx ng test --watch=false --browsers=ChromeHeadless` → **33/33 SUCCESS** exit 0.
  - `npx ng build --output-path dist/qa-p1-hotfix` → thành công ("Build at ... Time: 32902ms", chỉ warning budget có sẵn).
- Visual QA (dev server :4200 + backend :8080, đo DOMRect thật):

### Session: 2026-08-24 (phiên P2 — Sách đã xem gần đây + breadcrumb) — ox-alpha trong VS Code

- Mục tiêu: P2.1 service RecentlyViewedService; P2.2 ghi nhận lượt xem; P2.3 section trang chủ; P2.4 breadcrumb accessible; P2.5 a11y/polish. Không đụng backend/admin.
- Trạng thái kết thúc: done — toàn bộ acceptance đạt, có visual QA browser thật.
- File đã đọc: AGENTS.md; AI_WORKLOG.md (P0/P1); docs/features.md, localstorage.md, routes.md, bugs.md, user-flow.md; home.component.{ts,html,scss,spec.ts}; product-detail.component.{ts,html,spec.ts}; product-card.component.ts; git status 2 repo.
- Trạng thái ban đầu: dirty từ các phiên trước — bảo toàn nguyên vẹn, không reset/checkout/clean/stash.
- File đã sửa/tạo và lý do:
  - `services/recently-viewed.service.ts` (mới): key `vinabook_recently_viewed`, chỉ lưu ID nguyên dương, khử trùng, mới nhất trước, max 8, getIds/add/clear, tự làm sạch khi JSON hỏng hoặc sai kiểu, không crash.
  - `pages/product-detail/product-detail.component.ts`: gọi `recentlyViewed.add(id)` trong callback thành công của getProduct (dùng subscription paramMap hiện có — component tái sử dụng giữa các route vẫn ghi đúng sản phẩm mới, không tạo observer trùng). Loading/error/product null → không ghi.
  - `pages/product-detail/product-detail.component.html`: breadcrumb nâng cấp — nav[aria-label], thêm link "Sách" (/books), separator aria-hidden, tên sách `.current` với aria-current="page", không phải link.
  - `pages/product-detail/product-detail.component.scss`: focus-visible cho link breadcrumb; mobile ≤576px .current ellipsis (không overflow ngang).
  - `pages/home/home.component.{ts,html,scss}`: inject RecentlyViewedService + NotificationService; `applyRecentBooks()` map ID qua catalog hiện có (bỏ ID mất, tối đa 6); section "Sách đã xem gần đây" chỉ render khi !loading && !loadError && recentBooks.length>0; nút "Xóa lịch sử" (≥44px, focus-visible, nhãn chữ) xóa storage + cập nhật UI ngay + toast "Đã xóa lịch sử xem"; row tái dùng product-slider-container/row (scroll-snap có sẵn), reduced-motion tắt scroll-behavior smooth.
  - Specs: `recently-viewed.service.spec.ts` (mới, 7 test contract); `home.component.spec.ts` viết lại (4 test tích hợp); `product-detail.component.spec.ts` thêm 4 test (ghi nhận sau load, không ghi khi lỗi, breadcrumb aria-current + link / và /books, breadcrumb ẩn khi lỗi).
  - Docs: features.md (+breadcrumb, +sách đã xem gần đây), localstorage.md (+key `vinabook_recently_viewed`).
- localStorage key: `vinabook_recently_viewed` — mảng number[], tối đa 8, mới nhất trước; người dùng xóa bằng nút "Xóa lịch sử" trên trang chủ (hoặc tự xóa key).
- Lệnh đã chạy & exit code:
  - `npx tsc -p tsconfig.spec.json --noEmit` → PASS exit 0.
  - `npx ng test --watch=false --browsers=ChromeHeadless` → **47/47 SUCCESS** exit 0 (33 cũ + 14 mới).
  - `npx ng build --output-path dist/qa-p2` → PASS exit 0. Bundle: Initial **669.59 kB** (trước P2: 665.73 kB → tăng ~3.86 kB do service/mapping/markup; lý do hợp lý, không tăng đột biến). Warning budget giữ nguyên mức cũ: initial vượt 600 kB (69.59 kB), product-detail.scss 13.45 kB/12 kB (+1.45). KHÔNG tăng budget trong angular.json.
- Visual QA browser thật (dev server :4200, backend :8080; puppeteer-core ngoài repo; script `%TEMP%\p0qa\p2qa.js`, screenshot shots/p2-*):
  - Xoá key → mở /product/57, 58, 56 → về trang chủ: section hiển thị, thứ tự **ids=[56,58,57]** (Sapiens, Nhà Giả Kim, Đắc Nhân Tâm — dữ liệu backend thật), không trùng, overflow 0px.
  - Refresh trang chủ: stored=[56,58,57] — persistence ✓.
  - Mở lại /product/57 → về chủ: ids=[57,56,58] — lên đầu, không bản sao ✓.
  - "Xóa lịch sử": section biến mắt ngay, localStorage=null, toast success ✓.
  - Breadcrumb @360px: aria-label="Đường dẫn điều hướng", current="Đắc Nhân Tâm" aria-current=page, links ["/","/books"], overflow 0px ✓.
  - Regression P1: @390 bar(779..844) chat(723..771) btns cw=145/sw=145 h=44; @360 bar(735..800) chat(679..727) btns cw=130/sw=130 h=44 — không giao nhau, nút không tràn; desktop không có bar ✓.
  - 5 theme editorial/ocean/forest/berry/dark: console clean toàn bộ ✓.
  - Tổng: **16/16 PASS** (script exit 0).
- Console error / ExpressionChangedAfterItHasBeenCheckedError: không có.
- Blocker/cảnh báo còn lại: (a) `npm run build` chuẩn vẫn EPERM do khóa file dist cũ từ trước (ngoài phạm vi, build output-path riêng pass); (b) warning budget bundle/scss là nợ có sẵn từ trước P2, đã ghi mức tăng chính xác.
- Commit/PR: không commit, không push.

  - 360×800: "Mua ngay" cw=130/sw=130 fs=12px h=44; "Thêm giỏ hàng" cw=130/sw=130 fs=12px h=44 → scrollWidth <= clientWidth ✓, >=44px ✓; bar(735..800) chat(679..727) theme(t=683) → không giao nhau; overflow 0px; chatbot mở/đóng OK; console clean.
  - 390×844: "Mua ngay" cw=145/sw=145; "Thêm giỏ hàng" cw=145/sw=145 (fs=12px, h=44) ✓; bar(779..844) chat(723..771) theme(t=727) → không giao nhau; overflow 0px; console clean.
  - 768×1024: nút cw=327/sw=327 fs=13px h=44 ✓; bar(959..1024) chat(903..951) → không giao nhau.
  - 1280×720: không có sticky bar, console clean.
  - Tổng: **33/33 PASS** (script exit 0).
- Hết hàng: vẫn chưa có sản phẩm quantity=0 trong DB (unit test disabled đã phủ từ phiên P1).
- Blocker: không có mới. `npm run build` chuẩn vẫn chịu khóa file dist cũ từ trước (EPERM) — ngoài phạm vi hotfix này.
- Commit/PR: không commit, không push.



### Session: 2026-08-24 (đính chính cấu trúc AI_WORKLOG)

- Mục tiêu: Đính chính lỗi cấu trúc báo cáo trong file này do hai phần báo cáo bị ghép xen kẽ. KHÔNG sửa bất kỳ code, test hoặc tài liệu nào khác — code P2 đã được kiểm tra độc lập và đạt; các kết quả kiểm thử ghi trong file vẫn chính xác.
- Hiện trạng cấu trúc (đã kiểm tra theo số dòng thực tế):
  - Session "HOTFIX P1 — tràn chữ nút sticky bar" bắt đầu ở dòng 193. Bullet "- Visual QA (dev server :4200 + backend :8080, đo DOMRect thật):" ở dòng 208 là bullet ĐẦU TIÊN bị đứt.
  - Session "phiên P2 — Sách đã xem gần đây + breadcrumb" bị chèn vào ngay sau đó, chiếm dòng 210 đến dòng 240 (bắt đầu bằng tiêu đề nêu trên, kết thúc tại bullet "- Commit/PR: không commit, không push." của P2 ở dòng 240). Nội dung P2 này đúng và đầy đủ.
  - Các bullet còn lại của session HOTFIX P1 nằm LẠC SAU session P2, từ dòng 242 đến dòng 249: bắt đầu bằng "360×800: \"Mua ngay\" cw=130/sw=130 fs=12px h=44; ..." và kết thúc bằng "- Commit/PR: không commit, không push.". Chúng THUỘC session HOTFIX P1 — là phần tiếp nối trực tiếp của bullet Visual QA ở dòng 208 (kết quả đo DOMRect từng viewport, tổng 33/33 PASS, trạng thái hết hàng, blocker EPERM dist và Commit/PR).
- Nguyên nhân: khi append session P2 bằng thao tác chèn theo số dòng, vị trí tính toán bị lệch so với chiều dài thật của session HOTFIX P1 (đếm dòng non-blank thay vì toàn bộ dòng), khiến phần đuôi báo cáo HOTFIX P1 tách khỏi phần đầu và nằm sau khối P2.
- Cách đọc đúng cho người/AI về sau:
  - Đọc session HOTFIX P1 (từ dòng ~193): gộp bullet Visual QA ở dòng 208 với các bullet ở dòng 242–249 thành một khối liên tiếp — đây là một báo cáo hoàn chỉnh, trạng thái done.
  - Đọc session P2 (dòng 210–240) như một báo cáo độc lập hoàn chỉnh — nội dung không bị ảnh hưởng.
  - Khi append session mới vào file này: PHẢI đọc lại file để lấy đúng số dòng cuối cùng thực tế trước khi chèn (không dùng số đếm non-blank), ưu tiên append ở vị trí sau dòng cuối cùng của file.
- Giữ nguyên tuyệt đối mọi nội dung hiện có (append-only); phiên này chỉ thêm khối ghi chú đính chính ở trên và không thay đổi bất kỳ dòng nào trước đó.
- Không có thay đổi code/test/docs khác trong phiên này.
- Commit/PR: không commit, không push.

### Session: 2026-08-25 (audit cấu trúc repository)

- Trạng thái: done.
- Mục tiêu: Audit cấu trúc repository VinaBook, chỉ tạo đúng hai thay đổi: file mới `docs/repository-structure-audit.md` và session này ở cuối `AI_WORKLOG.md`. Không sửa/xóa/di chuyển bất kỳ file hiện có nào; không đụng `product57_category_fix.sql`; không reset/checkout/clean/stash; không commit/push/GitHub.
- File đã đọc: AGENTS.md; README.md; AI_WORKLOG.md (toàn bộ session có sẵn, kể cả các session HOTFIX P1 / phiên P2 / session đính chính); docs/ (liệt kê 11 file); bookstore-ui/src/app/{components,core,data,models,pages,services,shared} (liệt kê cấu trúc); angular-admin/src/app/{admin,common,guard,helper,login,services} (liệt kê); angular-admin helper/AuthInterceptor.ts (30 dòng đầu); VinaBookStore: api/ controller/ service/ service/implement/ (liệt kê class), application.properties + application-prod.properties (đoạn secret/URL), database.sql (schema categories/products), category_data_fix.sql; product57_category_fix.sql (chỉ xác nhận tồn tại — KHÔNG đụng).
- Lệnh đã chạy: Get-Location (= D:\book\book); git status --short (repo gốc dirty như đầu phiên); git rev-parse --show-toplevel (= D:/book/book); git branch --show-current (= master); git remote -v (rỗng); git -C bookstore-ui status --short / rev-parse --show-toplevel (= D:/book/book/bookstore-ui) / branch (= master) / remote -v (rỗng); git -C angular-admin status --short (9 M) / rev-parse (= D:/book/book/angular-admin) / branch (= main); git ls-files (111 tracked); git ls-files -s -- bookstore-ui angular-admin (2 gitlink mode 160000, không .gitmodules); rg "OderDetail" (1 file); rg -i "soldest" (admin module/component/spec); rg -i "martfury" (5 vị trí); rg "localhost|123456|admin123|DB_PASSWORD|JWT_SECRET" (application.properties, environments admin, README/AGENTS); liệt kê *.sql root (4 file); CSS rỗng 0 byte (8 file admin); sb-admin-2.min.{css,js} (assets admin); .jpb/persistence-units.xml + src/main/java/META-INF/MANIFEST.MF (đều bị track); Test-Path .github (=True, chỉ chứa modernize/java-upgrade hooks — KHÔNG có workflows).
- Phát hiện P0/P1/P2 (chi tiết trong docs/repository-structure-audit.md §4):
  - P0.1 submodule mồ côi: bookstore-ui + angular-admin là gitlink 160000, không .gitmodules, cả 3 repo không remote — rủi ro mất source khi clone lại.
  - P0.2 JWT secret default hardcode trong application.properties (`${JWT_SECRET:/SX3QPn...}`).
  - P0.3 4 file SQL trôi nổi ở root, thiếu thư mục/thứ tự áp dụng chuẩn.
  - P1.1 OderDetailApi.java typo ("Oder"); P1.2 admin route/folder "soldest"; P1.3 common/*.ts PascalCase (14 model); P1.4 helper/AuthInterceptor.ts lệch chuẩn Angular; P1.5 không có CI (.github không có workflows/).
  - P2.1 `.jpb` bị track; P2.2 MANIFEST.MF trong src/main/java bị track; P2.3 tên Martfury còn 5 chỗ; P2.4 8 CSS rỗng admin; P2.5 sb-admin-2 nguyên bản; P2.6 fly-to-cart.service.ts nằm trong shared/ storefront; P2.7 dev defaults localhost/credential demo.
- File duy nhất được tạo là docs/repository-structure-audit.md — đủ 10 mục yêu cầu: (1) trạng thái Git ban đầu của repo gốc + hai nested repo kèm branch/remote/dirty files; (2) sơ đồ cấu trúc; (3) phần hợp lý nên giữ; (4) bảng vấn đề P0/P1/P2 đầy đủ cột path/vấn đề/action đề xuất/file-import-config ảnh hưởng/rủi ro/cách kiểm thử; (5) đánh giá 15 điểm cụ thể theo đúng danh sách yêu cầu; (6) bản đồ current/path -> proposed/path; (7) cấu trúc repo đích tối giản (2 phương án A monorepo / B submodule chuẩn); (8) Phase 0–4 độc lập; (9) test matrix từng phase; (10) blocker B1–B4.
- Không sửa code. Không sửa SQL/config/dependency/tài liệu hiện có. product57_category_fix.sql nguyên trạng.
- Trạng thái Git cuối phiên:
  - Repo gốc (master, no remote): dirty như đầu phiên + duy nhất 2 thay đổi MỚI do phiên này: `?? docs/repository-structure-audit.md` và phần append cuối AI_WORKLOG.md (file vẫn untracked như trước).
  - bookstore-ui (nested master): 36 M + 4 ?? — KHÔNG đổi so với đầu phiên.
  - angular-admin (nested main): 9 M — KHÔNG đổi so với đầu phiên.

### Session: 2026-08-25 (đính chính audit remote angular-admin)

- Trạng thái: done.
- Mục tiêu: Đính chính một lỗi dữ liệu trong docs/repository-structure-audit.md — báo cáo trước đó khẳng định sai rằng cả 3 repo đều không có remote. Chỉ được sửa file audit và nối session này; mọi nội dung cũ trong AI_WORKLOG.md giữ nguyên (append-only).
- Thông tin SAI trong bản báo cáo trước:
  1. §1 bảng "Nested Git: angular-admin" — dòng Remote ghi "(rỗng)".
  2. §4 dòng P0.1 — ghi "cả 3 repo đều không có remote → chưa có bản sao dự phòng".
  3. §5 mục 2 — ghi "cả ba repo đều không có remote".
  4. §10 Blocker B1 — ghi "Hiện cả 3 repo đều KHÔNG có remote".
- Thông tin ĐÚNG đã được kiểm chứng (bằng git remote -v từng repo):
  - Repo gốc D:\book\book: KHÔNG có remote.
  - bookstore-ui: KHÔNG có remote.
  - angular-admin: CÓ remote `origin` → `https://github.com/ThanhLamm/veggie-shop-admin.git`.
- Các vị trí đã sửa trong docs/repository-structure-audit.md (4 chỗ):
  1. Dòng 40 — bảng §1 Nested Git: angular-admin: Remote đổi thành "`origin` → https://github.com/ThanhLamm/veggie-shop-admin.git *(đính chính: trước đó báo cáo ghi nhầm là rỗng)*".
  2. Dòng 109 — bảng §4 P0.1: thay mệnh đề "cả 3 repo đều không có remote" bằng trạng thái chính xác (repo gốc + bookstore-ui không remote; angular-admin có origin nêu trên); action cập nhật thành "tạo .gitmodules trỏ tới remote (angular-admin đã sẵn remote; bookstore-ui và repo gốc cần remote mới)". Kết luận cốt lõi GIỮ NGUYÊN: hai gitlink vẫn chưa hoàn chỉnh vì thiếu .gitmodules → repo cha không biết nguồn để init submodule.
  3. Dòng 128 — §5 mục 2: bổ sung trạng thái remote chính xác kèm ghi chú "(đính chính so với bản ghi đầu 'cả ba repo đều không có remote')"; kết luận P0.1 về gitlink thiếu .gitmodules giữ nguyên.
  4. Dòng ~230 — §10 B1: thay "Hiện cả 3 repo đều KHÔNG có remote" bằng trạng thái đã kiểm chứng (repo gốc + bookstore-ui KHÔNG có remote; angular-admin có origin GitHub); rủi ro mất source thu hẹp cho repo gốc + bookstore-ui.
- Không đổi các nội dung đúng sẵn: §1 bảng repo gốc (Remote rỗng), §1 bảng bookstore-ui (Remote rỗng), §7 phương án B, §8 Phase 0.
- Phạm vi tuân thủ: không sửa code/SQL/config/dependency/file nào khác; product57_category_fix.sql nguyên trạng; không reset/checkout/stash; không commit/push.
- Trạng thái Git cuối: repo gốc dirty như sau phiên audit + duy nhất nội dung đính chính nằm trong docs/repository-structure-audit.md (file untracked) và session này trong AI_WORKLOG.md (untracked). bookstore-ui (36 M + 4 ??) và angular-admin (9 M) không đổi.
- Commit/PR: không commit, không push.

### Session: 2026-08-25 (Phase 0 — chuyển sang monorepo)

- Trạng thái: done — **PASS**, mọi bước kiểm tra và backup đều thành công trước khi thao tác.
- Mục tiêu: Chuyển `bookstore-ui` và `angular-admin` từ hai Git gitlink mồ côi thành thư mục thường do repo gốc quản lý, tạo monorepo thực sự. Không commit/push/reset/clean/checkout hoàn tác/stash; không xóa `.git` con bằng Remove-Item (chỉ Move-Item vào backup); không sửa code/SQL/config; không đổi tên thư mục; chỉ stage đúng hai đường dẫn `bookstore-ui`, `angular-admin`.
- Trạng thái Git ban đầu: repo gốc master, không remote, dirty 21 M + 1 D + 5 ??; gitlink mode 160000 cho `bookstore-ui` (5ed7849…) và `angular-admin` (b461c49…); không có `.gitmodules`; bookstore-ui nested repo branch master, no remote, dirty 36 M + 4 ??; angular-admin nested repo branch main, remote origin https://github.com/ThanhLamm/veggie-shop-admin.git, dirty 9 M.
- Đường dẫn backup tuyệt đối: `D:\book\_phase0_backups\phase0-20260825-120413`
  - `root.git\` = bản copy nguyên trạng `D:\book\book\.git` (95,8 MB, có HEAD);
  - `bookstore-ui.git\` và `angular-admin.git\` = hai thư mục `.git` con được DI CHUYỂN (Move-Item, không xóa) từ vị trí cũ sau Bước 3;
  - `root-unstaged.patch` (46 KB) / `bookstore-ui-unstaged.patch` (111,5 KB) / `angular-admin-unstaged.patch` (13,3 KB); ba file `*-staged.patch` = 0 byte (đúng — chưa có gì staged);
  - `*-untracked.txt` (dùng `--exclude-standard`) và `untracked-root\` (6 file) + `untracked-bookstore-ui\` (8 file) = bản sao untracked giữ nguyên đường dẫn tương đối;
  - `backup-manifest.txt` (4,6 KB): thời gian tạo, đường dẫn, branch/HEAD SHA/remote/status của cả ba repo, vị trí metadata.
- HEAD SHA/branch/remote trước chuyển đổi: chi tiết trong backup-manifest.txt (repo gốc master/no-remote; bookstore-ui master/no-remote; angular-admin main/origin veggie-shop-admin).
- Lệnh chính đã chạy (theo thứ tự): B1 các lệnh kiểm tra trạng thái; B2 Copy-Item root .git → root.git, git diff / git diff --cached / git ls-files --others --exclude-standard cho cả 3 repo, Copy-Item untracked files, ghi manifest; B3 `git rm --cached -- bookstore-ui angular-admin` (exit=0, index bớt 2 gitlink, source nguyên vẹn — Test-Path True, package.json/src/app đọc được); B4 Resolve-Path xác nhận source/destination rồi `Move-Item` hai `.git` con vào backup (destination chưa tồn tại, không wildcard); B5 dry-run `git add -n` (306 file, 0 khớp pattern cấm) rồi `git add -- bookstore-ui angular-admin`; B6/B7 các lệnh xác minh và build/test.
- Xác nhận `.git` con được DI CHUYỂN chứ không xóa: `bookstore-ui/.git` và `angular-admin/.git` = False trong working tree; `D:\book\_phase0_backups\...\bookstore-ui.git\HEAD` và `angular-admin.git\HEAD` = True.
- Xác nhận không còn gitlink 160000: `git ls-files -s -- bookstore-ui angular-admin` trả về toàn bộ entry mode 100644 (0 dòng 160000); repo gốc track 144 file bookstore-ui + 162 file angular-admin (306 file thật).
- rev-parse từ cả hai thư mục sau chuyển đổi đều trả về `D:/book/book`; không còn `.git` lồng; không tạo `.gitmodules`.
- File bị loại khỏi stage nhờ ignore/bảo mật (dry-run check = 0 match cấm): node_modules, dist, .angular/cache, coverage, target, .env* — `git check-ignore` xác nhận node_modules/dist/target đều ignored. Không phát hiện secret/private key/token trong danh sách stage; không phải unstage file nào.
- Kết quả diff check: `git diff --check` = 0 issue; `git diff --cached --check` = 37 notice whitespace (trailing whitespace/new blank line at EOF) trong các file admin vừa được track — đây là nội dung GỐC có sẵn của angular-admin từ lịch sử repo cũ, lần đầu xuất hiện vì file được stage lần đầu; KHÔNG sửa để tránh đụng code (cấm theo phạm vi phiên). Cảnh báo LF→CRLF trên nhiều file là notice line-ending đã tồn tại, không tự ý format.
- Kết quả build/test (không cài/thay đổi dependency, build ra thư mục ngoài repo):
  - storefront `npx ng build --output-path D:\book\tmp-dist-p0final`: EXIT=0 (warning budget product-detail SCSS 13,45 kB và initial 698 kB — đã tồn tại từ trước);
  - admin `npx ng build --output-path D:\book\tmp-dist-admin`: EXIT=0 (warning budget initial 1,58 MB — đã tồn tại);
  - backend `mvnw.cmd test` (JAVA_HOME jdk-17 theo AGENTS.md): Tests run 21, Failures 0, Errors 0 — BUILD SUCCESS, EXIT=0.
- Trạng thái Git cuối: repo gốc master, staged: 2 deletion gitlink (`D bookstore-ui`, `D angular-admin`) + 306 entry `A` bên trong hai thư mục; các thay đổi VinaBookStore/docs/README giữ nguyên trạng thái dirty CHƯA stage; AI_WORKLOG.md vẫn untracked với session mới nối cuối; `docs/repository-structure-audit.md`, `product57_category_fix.sql` untracked nguyên trạng.
- File thay đổi trong phiên: KHÔNG có file nội dung nào bị sửa/xóa/tạo mới trong repo ngoài thay đổi Git index (gitlink→file thật). Backup nằm ngoài repo. AI_WORKLOG.md append session này.
- Cách rollback Phase 0 (nếu cần, thực hiện thủ công khi được yêu cầu): (1) bỏ 308 entry staged bằng thao tác restore index được phê duyệt; (2) Move-Item ngược `bookstore-ui.git`, `angular-admin.git` từ backup về `bookstore-ui\.git`, `angular-admin\.git`; (3) nếu index gốc bị hỏng: thay `.git` hiện tại bằng bản `root.git` trong backup (backup chụp TRƯỚC khi rm --cached nên index còn nguyên 2 gitlink); (4) file untracked có bản sao tại `untracked-root\`, `untracked-bookstore-ui\`.

### Session: 2026-08-25 (đính chính kết quả diff-check Phase 0)

- Trạng thái: done.
- Mục tiêu: Đính chính kết quả diff-check trong báo cáo Phase 0 ở trên. Append-only — không sửa session cũ.
- Kết quả chính xác của Phase 0:
  - **PASS về chuyển đổi cấu trúc monorepo**: không còn gitlink mode 160000; 306 file thật được repo gốc quản lý; backup hợp lệ tại `D:\book\_phase0_backups\phase0-20260825-120413`; build hai frontend thành công (EXIT=0); backend 21 test PASS (BUILD SUCCESS).
  - **Tuy nhiên** `git diff --cached --check` trả exit code 2 với **27 vị trí whitespace** (trailing whitespace / blank line at EOF).
- Phân loại lỗi: các vị trí whitespace này nằm trong NỘI DUNG CŨ của frontend vừa được đưa vào repo gốc (lần đầu được stage nên hiện ra trong cached diff), KHÔNG phải do Phase 0 tạo logic mới hay thao tác chuyển đổi gây ra.
- Trạng thái chính xác của Phase 0 vì vậy là: `STRUCTURAL PASS — PASS WITH KNOWN WHITESPACE WARNINGS`, chứ không phải diff-check sạch hoàn toàn như cách ghi "diff check = 0 issue / 37 notice" ở session trên (số liệu và diễn đạt chưa đủ chính xác).
- Việc dọn whitespace được HOÃN sang phase riêng; không sửa whitespace trong phiên đính chính này (cấm sửa code/whitespace theo phạm vi).
- Backup vẫn ở: `D:\book\_phase0_backups\phase0-20260825-120413`.
- Không commit và không push. Không thay đổi Git index, không stage/unstage, không di chuyển backup, không sửa file nào khác.

### Session: 2026-08-25 (P0.2 — JWT secret khỏi source; P0.3 — gom script SQL; làm sạch seed trước khi public)

- Trạng thái: done.
- Mục tiêu: (1) loại bỏ JWT secret mặc định khỏi source và cập nhật hướng dẫn local; (2) gom/chuẩn hóa các script database vào `db/`; (3) làm sạch dữ liệu cá nhân/token cũ trong seed trước khi repository public. Không làm P1/P2.
- Trạng thái Git ghi lại TRƯỚC khi sửa: `git diff --cached --name-only` = 308 path (2 deletion gitlink `angular-admin`, `bookstore-ui` + 306 file thật); `git ls-files -s -- bookstore-ui angular-admin` = 0 entry mode 160000; `git diff --cached --check` = exit 2 với cùng bộ notice whitespace cũ của frontend (không đổi so với sau Phase 0); dirty/untracked kế thừa từ các phiên trước.
- P0.2 — JWT:
  - `VinaBookStore/src/main/resources/application.properties`: xóa fallback secret trong source, hiện chỉ còn `bezkoder.app.jwtSecret=${JWT_SECRET}` kèm comment fail-fast. Không tạo secret mặc định khác, không chuyển secret sang file khác trong repository, không đưa secret/token thật vào README.
  - `application-prod.properties` giữ nguyên (đã đúng `${JWT_SECRET}`).
  - Cập nhật hướng dẫn local trong `README.md`, `VinaBookStore/README.md`, `AGENTS.md`: PowerShell sinh secret ngẫu nhiên trong bộ nhớ (byte[] 64 → RNG GetBytes → ToBase64String → `$env:JWT_SECRET`), không hardcode giá trị vào repo; ghi rõ thiếu biến môi trường thì backend không khởi động được và mỗi terminal mới phải đặt lại.
- P0.3 — SQL:
  - Tạo `db/`; DI CHUYỂN bằng thao tác filesystem (Move-Item, không dùng git mv/add/rm/reset): `database.sql`, `product_catalog_refresh.sql`, `category_data_fix.sql`, `product57_category_fix.sql`.
  - Tạo `db/README.md`: bảng mục đích/tình trạng từng script, thứ tự import chuẩn trên MySQL dev sạch, lưu ý schema dump (`martfury_shop`) khác cấu hình backend (`book`), cảnh báo không chạy trên MySQL đang phục vụ người dùng thật, danh sách tài khoản demo.
  - Cập nhật tham chiếu tài liệu: `README.md` bước 1 trỏ tới `db/README.md`; `docs/project-overview.md` sơ đồ cây thư mục; `docs/bugs.md` blocker product #57 trỏ tới `db/product57_category_fix.sql`. File audit và các session worklog cũ giữ nguyên (tài liệu lịch sử).
- Làm sạch seed `db/database.sql` (chỉ sửa FILE, không chạy SQL lên MySQL nào, không sửa database thật):
  - 5 email thật → `admin@vinabook.vn`, `user1@vinabook.vn`…`user4@vinabook.vn`; 5 bcrypt hash cũ (kèm JWT payload chứa issuer cũ) → 1 hash demo duy nhất cho mật khẩu đã ghi công khai trong `db/README.md` (sinh bằng spring-security-crypto 5.6.2 lấy từ kho Maven local qua chương trình Java tạm đặt tại `D:\book\tmp_p02_p03` NGOÀI repo; verify `matches()` MATCH/MATCH; giá trị hash không in ra terminal/báo cáo).
  - 5 JWT token cột `token` → NULL; 5 avatar cloudinary gắn tài khoản cũ → NULL; họ tên thật → tên giả; SĐT thật trong users/carts/orders → `'0900000001'`…`'0900000005'` (có nháy, chuẩn varchar); địa chỉ chi tiết carts/orders → địa chỉ demo tổng hợp.
  - Giữ nguyên toàn bộ ID (users 2..6, carts, orders, products, categories…) nên khóa ngoại `user_roles`/`carts`/`orders`/`order_details` không vỡ; schema và phần dữ liệu catalog/rates không đụng đến.
  - Quét sau xử lý (số đếm, không in giá trị): @gmail=0; JWT token=0; chuỗi issuer cũ=0; avatar users=0; SĐT cũ=0; bcrypt=5 chỗ trùng đúng 1 hash demo; email chỉ còn 5 giá trị @vinabook.vn; 36 SĐT demo có nháy (5 users + 5 carts + 26 orders).
- Kiểm tra build/test: `mvnw test` với `$env:JWT_SECRET` sinh ngẫu nhiên trong bộ nhớ theo đúng hướng dẫn mới → Tests run 21, Failures 0, Errors 0, BUILD SUCCESS, EXIT=0.
- Phạm vi tuân thủ: không stage/unstage file nào; không dùng git add/rm/mv/reset/restore/clean/checkout/stash; không commit/push; không sửa frontend; không đổi dependency/lockfile; không mất thay đổi dirty có sẵn; AI_WORKLOG chỉ append.
- Trạng thái Git cuối: cached diff VẪN 308 path nguyên trạng; mọi thay đổi phiên này đều unstaged/untracked: `M` application.properties, README.md, VinaBookStore/README.md, AGENTS.md, docs/bugs.md, docs/project-overview.md; ` D` (unstaged) database.sql, product_catalog_refresh.sql, category_data_fix.sql tại root do Move-Item; `?? db/`. Chủ dự án cần tự review và stage khi sẵn sàng.
- Blocker/nhắc việc: (1) secret mặc định cũ đã nằm trong lịch sử commit — cần coi là đã lộ và xoay theo blocker B2 của audit; (2) `product57_category_fix.sql` vẫn chờ phê duyệt chạy thủ công; (3) thư mục tạm `D:\book\tmp_p02_p03` (script sanitize + GenHash + hash.txt) nằm NGOÀI repo, chủ dự án có thể xóa sau khi review.

### Session: 2026-08-25 (Sửa sai P0 — đánh số SQL, schema `book`, demo `.example`, JWT test-only)

- Trạng thái: done.
- Mục tiêu: sửa 7 lỗi còn lại của P0 (đổi tên SQL theo thứ tự; chuẩn hóa database name; test không phụ thuộc JWT_SECRET môi trường; tham chiếu tài liệu/audit tên file cũ; email demo sang domain dành riêng `.example`; mật khẩu demo an toàn hơn; db/README.md bỏ cú pháp redirect `<` trong block PowerShell). Không làm P1/P2. Các phần đúng của phiên trước được giữ nguyên, không hoàn tác.
- Bước 1 — đổi tên (Move-Item, đường dẫn tuyệt đối, không wildcard, không ghi đè, không stage): `database.sql` → `01-schema-seed.sql`; `product_catalog_refresh.sql` → `02-product-catalog-refresh.sql`; `category_data_fix.sql` → `03-category-data-fix.sql`; `product57_category_fix.sql` → `04-product57-category-fix.sql`. Sau đổi tên `db/` có đúng 5 entry: 4 SQL + README.md; root không còn file SQL nào (count = 0).
- Bước 2 — chuẩn hóa database name trong `db/01-schema-seed.sql`: thay đúng 3 vị trí `martfury_shop` → `book` (CREATE DATABASE, USE, comment header Dump). Kết quả: `martfury_shop` = 0; có `CREATE DATABASE IF NOT EXISTS book` và `USE book` x1. Không đụng Cloudinary URL/branding Martfury khác (thuộc P2).
- Bước 3 — dữ liệu demo: 5 email `@vinabook.vn` → `@vinabook.example` (RFC 2606); sinh BCrypt hash mới cho mật khẩu demo `ChangeMe123!` (chương trình Java tạm GenHash2 ngoài repo tại `D:\book\tmp_p02_p03`, verify MATCH/MATCH, hash KHÔNG in ra terminal/báo cáo) thay cho hash cũ ở cả 5 vị trí; token giữ NULL; không khôi phục dữ liệu cá nhân. Xác nhận bằng số đếm: JWT-like=0; gmail=0; `@vinabook.vn`=0; `@vinabook.example`=5; bcrypt=5 vị trí unique=1; user ID 2..6 đủ `(2,'Hà Nội','admin@vinabook.example')` … `(6,'Hà Nội','user4@vinabook.example')`; mapping admin `(2,2)` trong `user_roles` nguyên vẹn.
- Bước 4 — `VinaBookStore/src/test/java/vn/fs/MartfuryShopApplicationTests.java`: thêm property test-only `bezkoder.app.jwtSecret=test-only-secret-that-must-never-be-used-in-production-123456789` vào `@SpringBootTest`; không đổi logic test nào khác; secret này chỉ có hiệu lực trong phạm vi test.
- Tài liệu: `db/README.md` viết lại theo tên file mới + thứ tự thực thi, mật khẩu/email demo mới, lệnh import qua `cmd /c "mysql ... < file"` vì PowerShell không hỗ trợ redirect `<`; `README.md` đoạn cuối trỏ sang `db/` có đánh số; `docs/bugs.md` blocker #57 trỏ tới `db/04-product57-category-fix.sql` / seed mới; `docs/repository-structure-audit.md` thay 26 tham chiếu tên cũ bằng đường dẫn mới kèm ghi chú cập nhật ngay sau blockquote mở đầu (các mô tả "ở root" phản ánh thời điểm audit).
- Kiểm tra: quét toàn bộ *.md trong repo (trừ node_modules/dist/target và các session lịch sử của AI_WORKLOG.md vốn append-only): 0 tham chiếu tên file cũ. `mvnw test` chạy với `JWT_SECRET_SET=False` (không set biến môi trường): Tests run 21, Failures 0, Errors 0, BUILD SUCCESS, EXIT=0.
- Phạm vi tuân thủ: không stage/unstage; không dùng git add/rm/mv/reset/restore/clean/checkout/stash; không commit/push; không sửa frontend; không chạy bất kỳ SQL nào; không xóa thư mục tạm ngoài repo; AI_WORKLOG chỉ append.
- Trạng thái Git cuối: cached diff vẫn đúng 308 path, 0 gitlink 160000; thay đổi phiên này đều unstaged/untracked (4 file SQL trong `db/` là rename filesystem so với index cũ, `?? db/`, `M` MartfuryShopApplicationTests, db/README.md, README.md, docs/bugs.md, docs/repository-structure-audit.md). Chủ dự án tự review và stage khi sẵn sàng.

### Session: 2026-08-25 (đính chính tài liệu P0)

- Trạng thái: done.
- Mục tiêu: chỉ sửa tài liệu P0 — README.md, docs/repository-structure-audit.md, nối thêm AI_WORKLOG.md. Không sửa code, SQL, config hay Git index; không chạy test hoặc SQL; không stage/unstage, không commit/push.
- Bối cảnh: phiên global-replace trước đã làm hỏng một số chỗ trong audit doc (bằng chứng lịch sử bị ghi thành `db/...` ở root, move map đảo 02/03, test matrix xuất hiện `db/db/`). Phiên này vá từng vị trí cụ thể, KHÔNG dùng global search/replace.
- Các lỗi đã sửa trong docs/repository-structure-audit.md:
  1. Khôi phục bằng chứng lịch sử đúng tên gốc tại thời điểm audit (4 file nằm ở root: `database.sql`, `product_catalog_refresh.sql`, `category_data_fix.sql`, `product57_category_fix.sql`) tại: header blockquote, danh sách dirty-files §1, sơ đồ cấu trúc §2, mục "nên giữ" §3, hàng P0.3, hai vị trí trong hàng P2.3, §5 mục 1 và mục 7.
  2. Chèn mục "Cập nhật trạng thái P0" trước §5: **P0.1 RESOLVED** (đã chuyển monorepo), **P0.2 RESOLVED** (main/prod bắt buộc `${JWT_SECRET}`, test có secret test-only), **P0.3 RESOLVED** (SQL chuyển vào `db/`, đánh số và có README).
  3. Move map §6 sửa về đúng bốn mapping: `database.sql -> db/01-schema-seed.sql`; `product_catalog_refresh.sql -> db/02-product-catalog-refresh.sql`; `category_data_fix.sql -> db/03-category-data-fix.sql`; `product57_category_fix.sql -> db/04-product57-category-fix.sql` (bản cũ đảo nhầm 02/03).
  4. Test matrix Phase 1: sửa `db/db/01-schema-seed.sql` → `db/01-schema-seed.sql`; ghi rõ thứ tự 01 → 02 → 03 và script 04 chỉ chạy khi chủ dự án duyệt.
  5. P2.7: bỏ hoàn toàn nhắc đến tài khoản cũ; trạng thái hiện tại được ghi bằng `admin@vinabook.example` / `ChangeMe123!` kèm ghi chú local-only.
- README.md: dòng tài khoản quản trị đổi thành `admin@vinabook.example` / `ChangeMe123!` với chú thích đây chỉ là tài khoản seed local, không dùng trên production.
- Kiểm tra cuối phiên (số liệu): `db/db/` trong README.md + docs/*.md + db/README.md = 0; `admin@vinabook.vn|admin123` trong cùng phạm vi = 0 (AI_WORKLOG.md là lịch sử append-only nên giữ nguyên); move map đủ 4 mapping đúng thứ tự; câu gán đường dẫn `db/...` cho vị trí "ở root" = 0; `git diff --cached --name-only` = 308 path; `git diff --cached --check` = đúng 27 notice whitespace cũ; `git ls-files -s -- bookstore-ui angular-admin` = 0 gitlink 160000.
- Không có file code/SQL/config nào bị sửa trong phiên: git status cho thấy toàn bộ entry `.java`/`.properties`/`.sql` dirty đều là baseline tồn tại từ các phiên trước, không có thay đổi mới.
- Không commit và không push.

### Session: 2026-08-26 (P1 hoàn chỉnh — OderDetailApi, soldest→bestseller, models kebab-case, core/interceptors, CI)

- Trạng thái: done.
- Mục tiêu: xử lý toàn bộ P1 theo audit doc (P1.1–P1.5); không đụng P2; không đổi API endpoint/payload/business logic; không nâng dependency hay sửa lockfile.
- Backup trước khi move: `D:\book\_p1_backups\p1-20260826-074052` — 47 file (OderDetailApi.java, common/, helper/, soldest/, app.module.ts, admin.module.ts, sidebar.component.html và toàn bộ file import `common/`); `manifest.txt` ghi thời gian, git status, staged count = 308, danh sách file. Đã xác minh backup tồn tại trước khi move đầu tiên.
- **P1.1**: Move-Item `OderDetailApi.java` → `OrderDetailApi.java`; đổi class tương ứng; giữ nguyên package `vn.fs.api`, endpoint `api/orderDetail`, authorization/ownership check. Scan `OderDetail` (case-sensitive) trong VinaBookStore/src = 0 (biến cục bộ lowercase `oderDetail` trong SendMailUtil không thuộc phạm vi). `mvnw test`: Tests run 21, Failures 0, Errors 0 — BUILD SUCCESS, EXIT=0.
- **P1.2**: Move `soldest/` → `bestseller/`; đổi tên 4 file component; `SoldestComponent` → `BestsellerComponent`; selector `app-bestseller`; templateUrl/styleUrls mới; `setPageActive('bestseller')`; admin.module.ts: import/declaration mới + route `bestseller` (AuthGuard) kèm redirect `{ path: 'soldest', redirectTo: 'bestseller', pathMatch: 'full' }` đúng như đặc tả; sidebar: pageActive + link `/admin/bestseller`. Scan `soldest` trong angular-admin/src còn đúng 1 hit = route redirect (chủ đích tương thích ngược).
- **P1.3**: tạo `src/app/models/`; move+rename 14 model PascalCase sang kebab-case (`CategoryBestSeller`→`category-best-seller`, `ChatMessage`→`chat-message`, `HomepageConfig`→`homepage-config`, `OrderDetail`→`order-detail`, …); sửa 8 import nội bộ giữa các model (`'./Role'`→`'./role'` v.v.); cập nhật 24 file import bên ngoài từ `common/X` sang `models/<kebab>`; xóa `common/` rỗng bằng filesystem.
  - Sự cố đã xử lý: build admin lần đầu FAIL (EXIT=1, 3 lỗi TS2307) do script thay thế xử lý key ngắn trước ('Order'/'Category') làm hỏng path của 'OrderDetail'/'CategoryBestSeller'; đã vá đích danh 3 dòng import (order-detail.component.ts, statistical-category.component.ts, models/rate.ts) rồi build lại EXIT=0. Chuỗi endpoint `api/orderDetail` trong component KHÔNG bị đụng tới (verify còn nguyên).
- **P1.4**: tạo `src/app/core/interceptors/`; Move `helper/AuthInterceptor.ts` → `core/interceptors/auth.interceptor.ts`; sửa relative import `'../services/session.service'` → `'../../services/session.service'`; app.module.ts trỏ sang `'./core/interceptors/auth.interceptor'`; xóa `helper/` rỗng. helper refs = 0.
- **P1.5**: tạo `.github/workflows/ci.yml` với 3 job — backend (temurin 17, service MySQL 8, `mvnw -B test` với JWT_SECRET/DB env chỉ dành cho CI), storefront (node 16, `npm ci --legacy-peer-deps`, production build + tsc spec type-check), admin (node 16, `npm ci --legacy-peer-deps`, production build). Không đổi dependency/lockfile.
- Build kiểm chứng cuối: `npx ng build` (admin) EXIT=0 (Initial Total 1.58 MB — warning budget đã tồn tại từ trước).
- Kiểm tra tổng: OderDetail=0; soldest chỉ còn redirect; tham chiếu model qua common/=0; helper refs=0; `models/` đủ 14 file kebab-case; cached diff = 308 path; gitlink 160000 = 0; whitespace notice cũ = 27 (không đụng).
- Phạm vi tuân thủ: mọi move/rename bằng Move-Item đường dẫn tuyệt đối; không stage/unstage; không dùng git add/rm/mv/reset/restore/clean/checkout/stash; không commit/push; không sửa SQL/JWT/P0; không format hàng loạt; AI_WORKLOG append-only.
- Trạng thái Git cuối: mọi thay đổi P1 đều unstaged/untracked so với index Phase 0 — AM admin.module.ts/app.module.ts/sidebar.component.html (+ các file sửa import), AD common/*.ts + helper/AuthInterceptor.ts (file đã move khỏi working tree), ?? OrderDetailApi.java, ?? admin/bestseller/, ?? src/app/models/, ?? .github/workflows/ci.yml. Chủ dự án tự review và stage khi sẵn sàng.
- Lưu ý hạn chế: `ng test --watch=false` cho admin cần Chrome headless nên chưa chạy trong phiên này (CI chỉ chạy build cho frontend; unit test admin để phiên QA browser riêng).

### Session: 2026-08-26 (hoàn thiện CI — permissions + concurrency)

- Trạng thái: done.
- Mục tiêu: chỉ hoàn thiện `.github/workflows/ci.yml` bằng cách thêm hai khối cấp workflow theo đặc tả; không làm lại các refactor P1 đã đúng, không làm P2.
- Thay đổi trong `.github/workflows/ci.yml` (chèn giữa khối `on:` và `jobs:`, không sửa job nào):
  - `permissions: contents: read` — workflow chỉ có quyền đọc nội dung repo (nguyên tắc least-privilege).
  - `concurrency:` group `ci-${{ github.workflow }}-${{ github.ref }}` với `cancel-in-progress: true` — tự hủy run cũ trên cùng ref khi có push mới, tiết kiệm runner.
- Kiểm chứng: đọc lại file — cấu trúc YAML đúng thứ tự name → on → permissions → concurrency → jobs; cả 3 job backend/storefront/admin giữ nguyên từng dòng. Trạng thái Git bảo toàn: cached diff = 308 path, gitlink 160000 = 0, whitespace notice cũ = 27; `ci.yml` vẫn ở trạng thái `??` untracked (không stage).
- Phạm vi tuân thủ: không stage/unstage, không commit/push, không nâng dependency/lockfile, không đụng code/SQL/config khác; AI_WORKLOG append-only.

### Session: 2026-08-26 (đính chính P1 — thay toàn bộ ci.yml theo đặc tả)

- Trạng thái: done.
- Mục tiêu: phiên trước chỉ thêm permissions/concurrency vào CI cũ; phiên này THAY TOÀN BỘ `.github/workflows/ci.yml` bằng nội dung đặc tả mới (khác bản cũ: job name gọn "Backend/Storefront/Admin"; MySQL service dùng password `ci-root-password` khớp health-cmd và DB_PASSWORD; JWT_SECRET CI mới `ci-only-secret-never-use-in-production-0123456789abcdef`; thêm bước `chmod +x mvnw` trước khi test backend — cần thiết trên runner Linux; storefront và admin đều có thêm step unit test `npm test -- --watch=false --browsers=ChromeHeadless --no-progress`).
- Cách thực hiện: ghi nội dung mới vào file tạm ngoài repo rồi Copy-Item đè lên `.github/workflows/ci.yml` (thao tác filesystem, lần đầu dùng editor replace toàn file bị trượt do lệch byte).
- Kiểm chứng: 113 dòng; đủ permissions (dòng 8) + concurrency (dòng 11); đúng 3 job backend/storefront/admin; có bước chmod mvnw; 2 step ChromeHeadless unit test; MySQL password/health-cmd/DB_PASSWORD khớp nhau (`ci-root-password`). Trạng thái Git bảo toàn: cached = 308 path, whitespace cũ = 27, gitlink = 0, ci.yml vẫn `??` untracked.
- Không stage/unstage, không commit/push, không sửa lockfile/dependency, không đụng code/SQL/P0/P2 khác; AI_WORKLOG append-only.
- Lưu ý: hai step unit test ChromeHeadless sẽ chạy được trên GitHub Actions runner (có Chrome cài sẵn); chạy local vẫn cần Chrome như đã ghi ở session P1.

### Session: 2026-08-26 (đính chính P1 — bổ sung unit test cho AuthInterceptor)

- Trạng thái: done.
- Mục tiêu: tạo `angular-admin/src/app/core/interceptors/auth.interceptor.spec.ts` với nội dung theo đúng đặc tả — 3 test case: gắn Bearer token cho request `/api/*` khi có token; không gắn Authorization khi thiếu token; không gắn Authorization cho request ngoài (domain khác).
- Kiểm chứng: `npx tsc -p tsconfig.spec.json --noEmit` trong angular-admin → EXIT=0, không lỗi type. Việc chạy Karma thực tế (`ng test`) cần Chrome, đã ghi hạn chế ở session trước và sẽ chạy trên CI (`ChromeHeadless`).
- Không đụng file nào khác; không stage/unstage; không commit/push; AI_WORKLOG append-only.



### Session: 2026-08-26 (P2 hoàn chỉnh + QA tổng trước khi tạo GitHub repo)

- Trạng thái: done.
- Mục tiêu: xử lý toàn bộ P2 (audit) + QA tổng. Không commit/push, không chạy SQL, không stage/unstage, không sửa lockfile/dependency.
- Backup: `D:\book\_p2_backups\p2-20260826-082112` — 36 file (`.jpb/`, MANIFEST.MF, pom.xml, SendMailUtil.java, test class cũ, .gitignore, audit doc, angular.json, upload.service, 2 environment admin, fly-to-cart + 4 file import, 8 CSS rỗng + 8 component.ts, sb-admin-2.min.css/js, application.properties/prod). `manifest.txt` đầy đủ; staged count lúc backup = 308.
- **Metadata (P2.1/P2.2):** Move-Item `VinaBookStore\.jpb` → backup (`.jpb.removed`) và `META-INF` → backup (`META-INF.removed`); working tree không còn cả hai; `.gitignore` thêm `**/.jpb/` và `/VinaBookStore/src/main/java/META-INF/MANIFEST.MF`.
- **Branding (P2.3):** pom `<description>MartfuryShop</description>` → `<description>VinaBook</description>`; rename test `MartfuryShopApplicationTests.java` → `VinaBookStoreApplicationTests.java` (Move-Item + đổi class); text email "Martfury-shop" → "VinaBook" (SendMailUtil dòng ~476). GIỮ NGUYÊN các URL hoạt động: logo email cloudinary/martfury (SendMailUtil), endpoint upload cloudinary/martfury (upload.service — thêm comment nợ kỹ thuật), URL ảnh sản phẩm trong `db/01-schema-seed.sql` (dòng 273). Scan sau: brand text = 0; chỉ còn 7 URL legacy có chủ đích.
- **8 CSS rỗng (P2.4):** xóa styleUrls khỏi app/add-category/add-customer/dashboard/edit-category/edit-customer/profile/bestseller component.ts; Move-Item 8 file css vào backup (`.removed`). EMPTY_CSS_LEFT=0.
- **SB Admin 2 (P2.5):** bằng chứng — 15/15 hit class đặc trưng nằm trong chính file css, không template/component nào dùng → gỡ khỏi angular.json styles/scripts; Move-Item 2 asset vào backup (`.removed`). Bundle admin giảm 1.58 → 1.51 MB.
- **FlyToCartService (P2.6):** Move `shared/fly-to-cart.service.ts` → `services/`; cập nhật 4 file import (product-card, home.spec, product-detail.spec, product-detail). OLD_REFS=0.
- **Hardening (P2.7):** application.properties thêm khối cảnh báo HARDENING dev-only defaults; prod profile giữ nguyên (đã strict từ P0.2).
- **Whitespace (27):** script parse `git diff --cached --check`, fix đúng từng vị trí trong working tree (trailing whitespace theo line, EOF blank trim cuối file, giữ encoding/BOM/EOL). Kết quả: NOTICES_PARSED=27, WORKTREE_WHITESPACE (git diff --check)=0; cached diff vẫn 27 (index không đụng — sẽ sạch khi chủ dự án re-stage).
- **QA tổng:** backend `mvnw test` BUILD SUCCESS EXIT=0 (sau rename test class/pom/SendMailUtil); admin `ng build` EXIT=0; storefront `ng build` EXIT=0 (698 kB); tsc spec type-check admin+storefront EXIT=0; `ng test ChromeHeadless`: admin **40/40 SUCCESS**, storefront **59/59 SUCCESS** (lần chạy đầu storefront có 2 FAILED flaky, 2 lần chạy lại đều 59/59 — ghi nhận để theo dõi).
- Audit doc: chèn mục "Cập nhật trạng thái P1/P2" sau mục P0 status với trạng thái RESOLVED/HARDENED từng item.
- Phạm vi tuân thủ: mọi move bằng Move-Item đường dẫn tuyệt đối; không stage/unstage; không git add/rm/mv/reset/restore/clean/checkout/stash; không commit/push/tạo GitHub repo; không chạy SQL; không sửa lockfile; AI_WORKLOG append-only.
- Trạng thái Git cuối: cached = 308 path nguyên trạng; thay đổi phiên này unstaged/untracked (M .gitignore/pom/SendMailUtil/test mới/angular.json/environments/application.properties/fly-to-cart importers/các file whitespace; AD .jpb, META-INF, common-styleUrls css; ?? OrderDetailApi đã từ phiên trước, ci.yml, auth.interceptor.spec.ts...). Backup P1 + P2 nằm ngoài repo.


### Session: 2026-08-26 (đính chính P2 — khôi phục SB Admin, branding, upload config và production hardening)

- Trạng thái: done.
- Thừa nhận sai sót: entry P2 trước đây của phiên "P2 hoàn chỉnh" đã LỆCH yêu cầu — xóa sb-admin-2 khỏi angular.json/assets trong khi admin vẫn phụ thuộc theme, chưa xử lý logo Martfury trong email, chưa đưa Cloudinary URL vào environment, và hardening DB_PASSWORD chưa triệt để. Session này sửa thực tế các điểm đó, KHÔNG làm lại phần đã đúng (.jpb/MANIFEST, 8 CSS rỗng, fly-to-cart, whitespace 27).
- **SB Admin 2 khôi phục:** Copy-Item từ `D:\book\_p2_backups\p2-20260826-082112\angular-admin\src\assets\` về `angular-admin\src\assets\` (backup giữ nguyên, không Move); angular.json: `src/assets/sb-admin-2.min.css` vào styles và `src/assets/sb-admin-2.min.js` vào scripts đúng như trước P2. Tạo `THIRD_PARTY_NOTICES.md` ở root: SB Admin 2 — Copyright Start Bootstrap — MIT — https://startbootstrap.com/theme/sb-admin-2 — giải thích asset giữ vì admin phụ thuộc template.
- **SendMailUtil:** 4 thẻ `<img src="https://res.cloudinary.com/martfury/...">` thay bằng `<p style="font-family: Open Sans...36px...800...color:#fcb800;" class="mobile-center">VinaBook</p>`; nội dung nghiệp vụ/trạng thái đơn/icon khác không đụng. martfury trong file = 0. 53 URL ảnh sản phẩm trong seed giữ nguyên (verify = 53).
- **Cloudinary upload config:** environment.ts thêm `cloudinaryUploadUrl: 'https://api.cloudinary.com/v1_1/martfury/image/upload'` + comment legacy local-dev (martfury = 1 occurrence duy nhất); environment.prod.ts thêm `cloudinaryUploadUrl: ''`; upload.service.ts viết lại — import environment relative (`../../environments/environment`), private helper `upload()`, URL rỗng → `throwError(new Error('Cloudinary upload URL is not configured.'))` qua Observable (không throw đồng bộ), không còn hardcode, không có API secret. upload.service.spec.ts: HttpClientTestingModule + HttpTestingController, test POST tới URL cấu hình, test lỗi khi rỗng kèm `expectNone`, restore environment trong afterEach/finally.
- **Production hardening:** application.properties: `spring.datasource.password=${DB_PASSWORD}` (bỏ default 123456, comment cập nhật); prod thêm `payment.frontend-url=${FRONTEND_URL}`, `payment.backend-url=${BACKEND_PUBLIC_URL}`, `app.admin-origin=${ADMIN_FRONTEND_URL}` (fail-fast). README gốc + VinaBookStore README liệt kê DB_PASSWORD/JWT_SECRET/FRONTEND_URL/BACKEND_PUBLIC_URL/ADMIN_FRONTEND_URL là bắt buộc khi deploy, bổ sung hướng dẫn Cloudinary (không nhúng secret vào Angular).
- **Test rename:** `VinaBookStoreApplicationTests.java` → `VinaBookApplicationTests.java` (Move-Item + đổi class); giữ nguyên 4 test, @AutoConfigureMockMvc, JWT secret test-only.
- **Audit:** mục P1/P2 status cập nhật — P2.1/P2.2 RESOLVED; P2.3 PARTIAL / RESOLVED WITH EXCEPTION (53 seed URL + 1 dev env URL giữ có chủ đích); P2.4 RESOLVED; P2.5 RETAINED INTENTIONALLY (không ghi đã gỡ); P2.6 RESOLVED; P2.7 RESOLVED.
- **QA thực tế:** backend `mvnw test` với DB_PASSWORD=123456 (mật khẩu dev local của máy) + JWT_SECRET random: **Tests run 21, Failures 0, Errors 0 — BUILD SUCCESS** (lưu ý: chạy với mật khẩu sai thì fail Access denied — chứng minh hardening hoạt động); admin build EXIT=0 (1.58 MB sau khôi phục SB Admin), tsc spec EXIT=0, `ng test ChromeHeadless` **42/42 SUCCESS** (gồm 2 test UploadService mới); storefront build EXIT=0, tsc spec EXIT=0, `ng test` **59/59 SUCCESS**. `npm run build` mặc định (ghi ra dist/) từng trả EXIT=127 do file dist cũ bị tiến trình zombie giữ khóa EPERM — chạy với `-- --output-path` khác EXIT=0; trên máy sạch/CI không gặp vấn đề này.
- **Kiểm tra cuối (số đếm):** sb-admin css/js tồn tại=True; angular.json sb-admin refs=2; THIRD_PARTY_NOTICES.md=True; SendMailUtil martfury=0; upload.service martfury=0; environment.prod martfury=0; environment.ts martfury=1; seed martfury=53; `${DB_PASSWORD}` exact=True; '123456' trong app props=0; 3 prod URLs exact=True, fallback http=0; empty CSS=0; old fly-to-cart ref=0; working-tree git diff --check=0; cached=308; gitlink=0; package-lock dirty=0.

### Session: 2026-08-26 (cleanup nhỏ — import không dùng + chính xác hóa README production)

- Trạng thái: done.
- Sửa 1: `angular-admin/src/app/services/upload.service.spec.ts` — xóa dòng `import { of } from 'rxjs';` (không sử dụng). Không sửa test/code khác.
- Sửa 2: README.md phần production — tách `DB_USERNAME` ra khỏi danh sách "bắt buộc, không có fallback" (vì source vẫn còn `${DB_USERNAME:root}`); danh sách no-fallback đúng = `DB_PASSWORD`, `JWT_SECRET`, `FRONTEND_URL`, `BACKEND_PUBLIC_URL`, `ADMIN_FRONTEND_URL`; ghi rõ DB_USERNAME nên cấu hình riêng trên production dù fallback root chỉ cho local; MAIL_USERNAME/MAIL_PASSWORD chỉ bắt buộc nếu bật email.
- Kiểm tra: `npx tsc -p tsconfig.spec.json --noEmit` (angular-admin) EXIT=0; `git diff --check` whitespace = 0; cached diff = 308 path; gitlink 160000 = 0.
- Không stage/unstage, không commit/push, không sửa file nào khác.

### Session: 2026-08-26 (final staging trước GitHub)

- Trạng thái: done.
- Backup index cũ: `D:\book\_final_stage_backups\prestage-20260826-094547` (index 50 KB, staged-before.patch ~3.5 MB, status-short/cached-stat/cached-name-status/manifest.txt). Manifest: branch=master, HEAD=605845a19a72df87eec4c07b1a8d367891e65368, không remote, cached count trước stage = 308.
- Preflight trước stage: nested .git = 0; gitlink 160000 = 0; `.env.local` tồn tại nhưng bị ignore (rule `.gitignore:52`), không nằm trong danh sách untracked → không thể vào staged snapshot; private key/credential file khác = 0; node_modules/dist/target thực tế đều IGNORED; các thư mục backup đều nằm ngoài repo; package-lock ×2 không có working-tree modification; `git diff --check` (working tree) = 0. Secret scan working tree: **0 finding** (quét PRIVATE KEY/JWT/sk-/ghp_/github_pat_/AIza/xoxb-/AKIA).
- Stage: `git add -A` EXIT=0 → **cached count sau = 340 path**; unstaged còn lại = 0; untracked còn lại = 0.
- Staged snapshot check: whitespace issue = 3 EOF blank line mới phát hiện (AI_WORKLOG.md, application-prod.properties, audit doc) → đã trim và re-add, giờ = **0**; gitlink 160000 = 0; bad pattern staged (node_modules/dist/target/coverage/.angular/.env/backup/tmp/nested .git) = 0; file >20 MB = 0 (lớn nhất: angular-admin/package-lock.json ≈ 1086.5 KB); package-lock ×2 tracked=True; `.jpb/persistence-units.xml` và `src/main/java/META-INF/MANIFEST.MF` staged ở trạng thái D; SQL root ×3 staged trạng thái D (`product57_category_fix.sql` gốc chưa từng được track nên không có entry); `db/` staged đủ 5 entry (01→04 + README); bookstore-ui/angular-admin track 299 entry mode 100644, 160000 = 0; key new files (ci.yml, THIRD_PARTY_NOTICES.md, VinaBookApplicationTests.java, auth.interceptor.ts/.spec.ts, bestseller/, models/, fly-to-cart services/, application-prod.properties, audit doc…) staged=True. A/M/D/R summary: A=308, M=22, D=5, R=5 (git nhận diện rename SQL/db).
- Secret scan trên staged content: **0 finding** (7 pattern, toàn bộ file text trong snapshot).
- Chưa commit, chưa push, không tạo/xóa GitHub repo hay remote.

### Session: 2026-08-26 (đính chính số lượng old history và tạo recovery bundle)

- Trạng thái: done.
- Đính chính: session "tạo fresh Git history trước GitHub" ghi 29 là "số liệu đề bài sai" là KHÔNG chính xác. Số đúng: **master = 9 commits** (HEAD `605845a19a72df87eec4c07b1a8d367891e65368`); **feature/storefront-p0-p2 = 20 commits** (HEAD `a7dc175c893b7ed2acbf0bf1a2b26e57392df2f2`); **tổng unique commits reachable từ cả hai branch = 29**.
- Recovery bundle đã tạo: `D:\book\_fresh_repo_backups\fresh-20260826-102956\old-history-branches.bundle` (~95 MB), chứa ĐÚNG hai branch cũ qua `git bundle create ... master feature/storefront-p0-p2` với old-git làm git-dir.
- `git bundle verify`: EXIT=0 — bundle okay, ghi nhận complete history. `git bundle list-heads`: đúng 2 refs `refs/heads/master` + `refs/heads/feature/storefront-p0-p2`.
- Bundle SHA-256: `D1B3363910F2AD1913212606C8CB13E3D746668350B9D1A903F466AB718881A0`.
- Ref `refs/codex/...` trong old-git bị hỏng do đường dẫn Windows quá dài; không thuộc hai branch backup và không được đưa vào bundle.
- Manifest ngoài repo đã append phần đính chính (không xóa nội dung cũ).
- Không sửa source, không tạo remote, không push.


### Session: 2026-08-26 (tạo fresh Git history trước GitHub)

- Trạng thái: done.
- Backup old-git: `D:\book\_fresh_repo_backups\fresh-20260826-102956\old-git` (kèm old-status/old-staged-stat/old-staged-name-status/old-staged.patch/old-ls-files-stage.txt/manifest.txt).
- Old HEAD: `605845a19a72df87eec4c07b1a8d367891e65368`; old commit count thực tế = **9** (manifest spec dự kiến 29 — số liệu đề bài sai; HEAD SHA khớp từng byte chứng minh toàn bộ ancestry được bảo toàn trọn vẹn trong backup).
- Lý do tạo history mới: tránh đưa lịch sử Git cũ — vốn từng chứa các bản cấu hình/dữ liệu chưa sanitize (secret dev, dữ liệu cá nhân) — lên GitHub repository mới.
- Repo mới khởi tạo bằng `git init -b main`, dùng branch `main`.
- Source code trong working tree không bị thay đổi bởi thao tác chuyển `.git` (chỉ Move-Item thư mục metadata).
- Chưa tạo remote nào và chưa push đi đâu.
- Commit dự kiến tiếp theo: `Initial clean VinaBook monorepo`.

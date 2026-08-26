# Audit cấu trúc repository — VinaBook (D:\book\book)

> Phiên audit 2026-08-25. Đây là tài liệu **chỉ đọc/phân tích**: không có file code,
> SQL, config hay tài liệu hiện có nào bị thay đổi trong phiên tạo ra tài liệu này.
> Mọi kết luận đều kèm bằng chứng từ `git ls-files`, `rg` hoặc liệt kê filesystem.
> File `product57_category_fix.sql` ở root là sản phẩm của phiên trước, nằm ngoài
> phạm vi audit và không được đụng đến trong phiên này.
>
> **Cập nhật phiên sửa sai P0 (2026-08-25):** các script SQL đã được chuyển từ root vào thư mục `db/` và đổi tên theo thứ tự thực thi: `01-schema-seed.sql`, `02-product-catalog-refresh.sql`, `03-category-data-fix.sql`, `04-product57-category-fix.sql`. Các đoạn mô tả vị trí "đặt ở root" dưới đây phản ánh trạng thái thời điểm audit.

## 1. Trạng thái Git ban đầu

### Repo gốc (D:\book\book)

| Mục | Giá trị |
|---|---|
| `Get-Location` | `D:\book\book` |
| `git rev-parse --show-toplevel` | `D:/book/book` |
| `git branch --show-current` | `master` |
| `git remote -v` | *(rỗng — chưa cấu hình remote)* |
| `git status --short` | dirty: 21 file `M` + 1 file `D` (SendMailApi.java) + 5 mục `??` |
| Số file tracked (`git ls-files`) | 111 |

Dirty files của repo gốc: README.md; VinaBookStore (README, api/FavoritesApi, api/OrderApi, api/SendMailApi **deleted**, api/UserApi, config/JwtUtils, config/WebSecurityConfig, config/WebSocketConfiguration, controller/ResetPasswordController, handler/NotificationWebSocketHandle, application.properties, test/MartfuryShopApplicationTests); docs (architecture, bugs, features, localstorage, roadmap, routes); `?? AI_WORKLOG.md`; `?? VinaBookStore/src/main/java/vn/fs/config/JwtHandshakeInterceptor.java`; `?? VinaBookStore/src/main/resources/application-prod.properties`; `?? VinaBookStore/src/test/java/vn/fs/config/`; `?? product57_category_fix.sql`.

### Nested Git: bookstore-ui

| Mục | Giá trị |
|---|---|
| `git -C bookstore-ui rev-parse --show-toplevel` | `D:/book/book/bookstore-ui` |
| Branch | `master` |
| Remote | *(rỗng)* |
| `bookstore-ui/.git` | **thư mục** repo Git đầy đủ (PSIsContainer = True), không phải file gitlink trên disk |
| Status | dirty 36 `M` + 4 `??` (forgot-password/, fab-collision*, recently-viewed*) |

### Nested Git: angular-admin

| Mục | Giá trị |
|---|---|
| `git -C angular-admin rev-parse --show-toplevel` | `D:/book/book/angular-admin` |
| Branch | `main` |
| Remote | `origin` → `https://github.com/ThanhLamm/veggie-shop-admin.git` *(đính chính: trước đó báo cáo ghi nhầm là rỗng)* |
| Status | dirty 9 `M` (dashboard, edit-customer×2, header, order, profile, app.component.spec, helper/AuthInterceptor.ts, test.ts) |

### Quan hệ cha–con

`git ls-files -s -- bookstore-ui angular-admin` trả về đúng 2 entry:

```
160000 b461c490e918f0f3aaf0254ea66109b2f610c6b8 0  angular-admin
160000 5ed7849434d6501f9e79fa83e70db2388e1a882e 0  bookstore-ui
```

→ Repo cha track hai thư mục như **gitlink (submodule) nhưng `.gitmodules` KHÔNG tồn tại**
(`Test-Path .gitmodules` = False). Đây là trạng thái submodule mồ côi: clone lại repo cha
sẽ chỉ nhận được con trỏ commit rỗng, không có cách nào biết nguồn để init/update.

## 2. Sơ đồ cấu trúc hiện tại

```
D:\book\book\                          (repo master, 111 tracked files)
├── .github/
│   └── modernize/java-upgrade/hooks/scripts/   ← tool hook, KHÔNG có workflows/CI
├── .run-logs/                         (ignored)
├── AGENTS.md / README.md / AI_WORKLOG.md (untracked)
├── database.sql                         (51 KB seed, product_id 3..55)
├── category_data_fix.sql              (data fix #8/#9)
├── product_catalog_refresh.sql        (25 KB)
├── product57_category_fix.sql         (mới, ngoài phạm vi phiên này)
├── docs/                              (11 file *.md)
├── bookstore-ui/                      (NESTED REPO master — storefront Angular 12)
│   └── src/app/{components,core,data,models,pages,services,shared}
│       ├── core/{guards,interceptors,layouts,services}
│       ├── shared/{components,directives,pipes,toast} + fly-to-cart.service.ts
│       ├── models/{common,homepage-config.ts}
│       └── data/payment-methods.ts
├── angular-admin/                     (NESTED REPO main — admin Angular 12)
│   └── src/app/{admin,common,guard,helper,login,services}
│       ├── common/*.ts                (14 model PascalCase)
│       ├── helper/AuthInterceptor.ts
│       └── assets/sb-admin-2.min.{css,js} + 8 component CSS rỗng
└── VinaBookStore/                     (Spring Boot 2.6 / Java 17)
    ├── .jpb/persistence-units.xml     (IDE artifact bị track)
    └── src/main/java/vn/fs/
        ├── api/       (14 REST controller *Api.java, gồm OderDetailApi.java typo)
        ├── controller/(1 file: ResetPasswordController — server-rendered)
        ├── config/ handler/ dto/ entity/ repository/
        ├── service/ + service/implement/ (3 class)
        ├── utils/SendMailUtil.java        (còn chữ Martfury)
        └── META-INF/MANIFEST.MF           (trong src/main/java — bất thường)
```

## 3. Những phần hợp lý nên giữ

- **Phân tầng backend hiện tại**: `api/` = REST API stateless, `controller/` = MVC
  server-rendered (reset password). Phân chia này nhất quán và có chủ đích — chỉ cần
  ghi vào docs/architecture.md, không cần gộp.
- **Storefront**: `core/` (guards/interceptors/layouts) vs `shared/` (UI tái sử dụng) là
  ranh giới chuẩn Angular; `ProductService` làm nguồn catalog duy nhất đã hoạt động tốt.
- `docs/` đầy đủ, `AGENTS.md` + `AI_WORKLOG.md` làm điểm phối hợp.
- Seed `database.sql` + các script data-fix idempotent có verify query đi kèm.
- Quy ước đặt tên page theo thư mục trong `pages/`, spec song song component.


## 4. Bảng vấn đề theo mức ưu tiên

Mức: **P0** = rủi ro mất dữ liệu/bảo mật/chặn trở phát triển; **P1** = nợ kỹ thuật gây chi phí lặp lại; **P2** = vệ sinh/tính nhất quán.

| # | Path hiện tại | Vấn đề | Path/action đề xuất | File/import/config bị ảnh hưởng | Rủi ro | Cách kiểm thử |
|---|---|---|---|---|---|---|
| P0.1 | `bookstore-ui`, `angular-admin` (gitlink mode 160000) + thiếu `.gitmodules` | Submodule mồ côi: clone lại repo cha không kéo được source; repo gốc và bookstore-ui không có remote, angular-admin có remote `origin` → `https://github.com/ThanhLamm/veggie-shop-admin.git`; hai gitlink vẫn chưa hoàn chỉnh vì thiếu `.gitmodules` → repo cha không biết nguồn để init submodule | Quyết định mô hình (§10-B1): hoặc gộp monorepo thật (`git rm --cached` hai gitlink + track file trực tiếp), hoặc tạo `.gitmodules` trỏ tới remote (angular-admin đã sẵn remote; bookstore-ui và repo gốc cần remote mới) | Index repo cha, `.gitmodules` mới | Mất source nếu máy hỏng; clone mới không build được | Sau đổi: `git ls-files -s` đúng mô hình; clone thử vào thư mục tạm rồi build 3 app |
| P0.2 | `VinaBookStore/src/main/resources/application.properties:29` | JWT secret có giá trị mặc định hardcode trong repo (`${JWT_SECRET:/SX3QPn...}`) | Bỏ default, bắt buộc `${JWT_SECRET}` như application-prod.properties đã làm; xoay secret | application.properties, JwtUtils | Ai đọc repo ký được token nếu secret dev dùng ngoài máy dev | `mvnw test`; khởi động local phải fail-fast khi thiếu biến môi trường |
| P0.3 | Root: 4 file SQL trôi nổi (`database.sql`, `category_data_fix.sql`, `product_catalog_refresh.sql`, `product57_category_fix.sql`) | Không biết script nào đã chạy trên DB nào; không có thứ tự áp dụng chuẩn | Gom vào `db/` kèm README ghi thứ tự + tính idempotent; lâu dài chuyển Flyway/Liquibase | Chỉ di chuyển file + cập nhật README/docs | Chạy nhầm/sót script làm dữ liệu lệch giữa môi trường | Import db/01-schema-seed.sql sạch vào MySQL dev, chạy lần lượt theo README, verify query từng script ra đúng kỳ vọng |
| P1.1 | `VinaBookStore/src/main/java/vn/fs/api/OderDetailApi.java` | Sai chính tả tên file/class ("Oder" thiếu 'r') — bằng chứng `rg OderDetail` | Rename thành `OrderDetailApi.java` + sửa mọi tham chiếu | Tên class, chỗ inject/import backend, test liên quan | Khó grep/onboarding, hiểu nhầm API order detail | `rg OderDetail` = 0 match; `mvnw test` xanh |
| P1.2 | `angular-admin/src/app/admin/soldest/` + route `soldest` | Tên "soldest" khó hiểu (bestseller theo sold?) | Đổi route/folder/component thành `bestseller`, thêm redirect từ route cũ | admin.module.ts (import/route/declarations), pageService.setPageActive('soldest'), selector app-soldest, template menu | Menu/link admin hỏng nếu rename sót | Build admin; bấm qua menu dashboard; `rg soldest` chỉ còn redirect |
| P1.3 | `angular-admin/src/app/common/*.ts` (14 model PascalCase) | Model đặt tên file PascalCase ngược convention Angular; docs/architecture.md còn cảnh báo storefront spec import `common/` chỉ tồn tại ở admin | Đổi kebab-case hoặc gộp vào `models/`; sửa toàn bộ import `common/X` | Các import trong services/admin components của admin | Build fail nếu sót import | `ng build` admin + `ng test --watch=false` |
| P1.4 | `angular-admin/src/app/helper/AuthInterceptor.ts` | Thư mục `helper/` + tên file PascalCase, khác chuẩn Angular (`core/interceptors/auth.interceptor.ts` như storefront) | Chuyển sang `core/interceptors/auth.interceptor.ts`, giữ nguyên logic | provider HTTP_INTERCEPTORS trong app.module.ts admin | Interceptor mất đăng ký nếu sửa sai provider | Login admin → request phải mang `Authorization: Bearer`; build/test xanh |
| P1.5 | `.github/` (chỉ có modernize hook), không có `.github/workflows/` | Không CI cho 3 app; mỗi lần sửa phải tự nhớ chạy 3 bộ lệnh riêng | Thêm workflow build+test matrix (Phase 4) | `.github/workflows/ci.yml` mới | Regression trôi vào branch chính không bị phát hiện | Push branch thử → CI chạy đủ 3 job xanh |
| P2.1 | `VinaBookStore/.jpb/persistence-units.xml` | IDE artifact (JPA Buddy) bị track | `git rm --cached` + ignore `.jpb/` | .gitignore | Noise diff, lộ cấu hình IDE | `git status` sạch; build vẫn xanh |
| P2.2 | `VinaBookStore/src/main/java/META-INF/MANIFEST.MF` | MANIFEST trong source tree; Maven tự sinh manifest khi đóng gói | Bỏ track + ignore sau khi xác nhận không ai tham chiếu; nếu cần manifest custom thì cấu hình maven-jar-plugin | pom.xml (nếu cấu hình thêm) | Conflict merge vô nghĩa, stale metadata | `mvnw package`; `java -jar` chạy OK |
| P2.3 | Tên Martfury còn 5 chỗ: `VinaBookStore/pom.xml`, `src/test/java/vn/fs/MartfuryShopApplicationTests.java`, `src/main/java/vn/fs/utils/SendMailUtil.java`, `database.sql`, `angular-admin/src/app/services/upload.service.ts` | Thương hiệu template cũ còn sót | Rename artifactId/lớp test (`VinaBookStoreApplicationTests`), sửa text mail, comment SQL, chuỗi service admin | pom.xml, test class, SendMailUtil, upload.service.ts, database.sql | Grep "vinabook" không phủ hết; nhầm brand | `rg -i martfury` = 0 match; `mvnw test` + build admin xanh |
| P2.4 | 8 CSS rỗng trong angular-admin: `app.component.css`, `admin/{add-category,add-customer,dashboard,edit-category,edit-customer,profile,soldest}/*.component.css` (0 byte) | File scaffold CLI để lại | Xóa file + bỏ `styleUrls` tương ứng | decorator @Component từng file | Noise, gây nhầm có styling | Build admin; UI không đổi |
| P2.5 | `angular-admin/src/assets/sb-admin-2.min.css`, `sb-admin-2.min.js` | Theme SB Admin 2 nguyên bản load toàn cục | Giữ nếu đang phụ thuộc class; ghi nhận nợ kỹ thuật, về sau tách phần dùng hoặc thay theme riêng | angular.json styles/scripts hoặc index.html của admin | Bundle nặng, dead-code CSS xung đột | So sánh render admin trước/sau trong phase tối ưu riêng |
| P2.6 | `bookstore-ui/src/app/shared/fly-to-cart.service.ts` | Service nằm trong thư mục UI `shared/` thay vì `services/` | Chuyển về `services/fly-to-cart.service.ts` | import ở header/product-detail/cart component | Service nghiệp vụ phân tán, khó tìm | `rg fly-to-cart` sau move; build + test storefront xanh |
| P2.7 | Dev defaults: `application.properties` (localhost URLs, `${DB_PASSWORD:123456}`), `angular-admin/src/environments/environment.ts` (localhost 8080/4200/ws), README (tài khoản seed local `admin@vinabook.example`/`ChangeMe123!` — chỉ dùng local) | Giá trị dev hardcode dạng env-fallback — chấp nhận được cho dev nhưng dễ quên override khi deploy | Giữ pattern `${ENV:default}` cho dev; prod đã bắt buộc env qua application-prod.properties; bổ sung docs "dev-only defaults" | application.properties, environments admin | Quên override khi deploy staging/prod | Checklist release: mọi biến môi trường prod khác default; deploy staging không dùng secret dev |

### Cập nhật trạng thái P0 (phiên thực thi + sửa sai, 2026-08-25)

- **P0.1 RESOLVED:** hai gitlink mồ côi đã chuyển thành monorepo thật — repo cha quản lý trực tiếp 306 file của storefront/admin, không còn entry mode 160000.
- **P0.2 RESOLVED:** `application.properties` (main) và `application-prod.properties` đều bắt buộc `${JWT_SECRET}`, không còn secret mặc định trong source; test backend dùng secret test-only qua property `@SpringBootTest`, không phụ thuộc biến môi trường.
- **P0.3 RESOLVED:** bốn script SQL đã chuyển vào `db/`, đánh số theo thứ tự thực thi và có `db/README.md` hướng dẫn import.

### Cập nhật trạng thái P1/P2 (phiên hoàn thiện 2026-08-26)

- **P1.1 RESOLVED:** `OderDetailApi.java` → `OrderDetailApi.java` (file + class); endpoint `api/orderDetail`, authorization/ownership giữ nguyên; `OderDetail` trong source = 0.
- **P1.2 RESOLVED:** `soldest/` → `bestseller/`; `BestsellerComponent` (selector `app-bestseller`); route `/admin/bestseller` (AuthGuard) + redirect `{ path: 'soldest', redirectTo: 'bestseller', pathMatch: 'full' }`; sidebar dùng `bestseller`.
- **P1.3 RESOLVED:** 14 model `common/*.ts` → `models/<kebab-case>.ts`; 24 file import cập nhật sang `models/<kebab>`; thư mục `common/` đã xóa.
- **P1.4 RESOLVED:** `helper/AuthInterceptor.ts` → `core/interceptors/auth.interceptor.ts`; app.module trỏ sang path mới; unit test mới `auth.interceptor.spec.ts` (3 case).
- **P1.5 RESOLVED:** `.github/workflows/ci.yml` 3 job — backend (MySQL service, chmod mvnw, `mvnw -B test`), storefront/admin (`npm ci --legacy-peer-deps`, build, tsc spec type-check, `ng test --browsers=ChromeHeadless`); `permissions: contents: read` + `concurrency cancel-in-progress`.
- **P2.1/P2.2 RESOLVED:** `.jpb/` và `src/main/java/META-INF/MANIFEST.MF` Move-Item ra backup; `.gitignore` thêm `**/.jpb/` và rule manifest.
- **P2.3 PARTIAL / RESOLVED WITH EXCEPTION:** pom `<description>` → VinaBook; test class → `VinaBookApplicationTests`; text email "Martfury-shop" → "VinaBook"; logo Martfury trong email thay bằng text "VinaBook"; upload Cloudinary chuyển sang `environment.cloudinaryUploadUrl` (prod rỗng). Chỉ còn **53 URL ảnh sản phẩm legacy trong `db/01-schema-seed.sql`** và **1 URL dev trong `angular-admin/src/environments/environment.ts`** được giữ có chủ đích.
- **P2.4 RESOLVED:** 8 CSS rỗng chuyển vào backup; bỏ `styleUrls` khỏi 8 component tương ứng; build admin xanh.
- **P2.5 RETAINED INTENTIONALLY:** SB Admin 2 được **giữ lại** (asset + tham chiếu trong angular.json) vì giao diện admin còn phụ thuộc theme này; attribution ghi trong `THIRD_PARTY_NOTICES.md`. KHÔNG ghi là đã gỡ.
- **P2.6 RESOLVED:** `fly-to-cart.service.ts` về `bookstore-ui/src/app/services/`; cập nhật 4 file import.
- **P2.7 RESOLVED:** `DB_PASSWORD` không còn mặc định (`${DB_PASSWORD}`); prod bắt buộc thêm `FRONTEND_URL`, `BACKEND_PUBLIC_URL`, `ADMIN_FRONTEND_URL` (fail-fast); JWT_SECRET đã bắt buộc từ P0.2.
- **Whitespace:** 27 vị trí cũ trong cached diff đã sửa xong ở working tree (cached diff giữ nguyên tới khi chủ dự án re-stage).



## 5. Đánh giá cụ thể theo yêu cầu

1. **Ba file SQL ở root** (thực tế hiện có 4, tính `product57_category_fix.sql` sinh từ phiên trước — ngoài phạm vi audit): `database.sql` là seed gốc 51 KB (product_id 3..55), `category_data_fix.sql` là data-fix có transaction + verify query, `product_catalog_refresh.sql` 25 KB. Vấn đề chung: không có thư mục quy ước, không ghi thứ tự áp dụng hay trạng thái đã chạy trên môi trường nào → P0.3.
2. **Nested .git và gitlink không có .gitmodules**: `bookstore-ui/.git` và `angular-admin/.git` là thư mục repo đầy đủ; repo cha track chúng như gitlink 160000 nhưng `.gitmodules` không tồn tại; remote: repo gốc và bookstore-ui không có, angular-admin có `origin` → `https://github.com/ThanhLamm/veggie-shop-admin.git` (đính chính so với bản ghi đầu "cả ba repo đều không có remote") → P0.1.
3. **Backend api/ và controller/**: `api/` chứa 14 REST controller (`*Api.java`), `controller/` chỉ chứa `ResetPasswordController` phục vụ trang server-rendered cho luồng reset password. Phân chia có chủ đích và nhất quán — **nên giữ**, chỉ cần document hóa trong docs/architecture.md.
4. **OderDetailApi**: `VinaBookStore/src/main/java/vn/fs/api/OderDetailApi.java` tồn tại thật (bằng chứng `rg OderDetail`) — typo "Oder" → P1.1.
5. **service/implement**: interface ở `service/` (`AiGateway`, `ChatRateLimiter`, `ChatService`, `PaymentGatewayService`, `SendMailService`) và implementation ở `service/implement/` (`SendMailServiceImplement`, `UserDetailsImpl`, `UserDetailsServiceImpl`). Pattern kiểu Spring truyền thống nhưng nhất quán toàn project — khuyến nghị giữ nguyên, chỉ xem xét đổi khi nâng cấp Spring Boot.
6. **`.jpb` và META-INF/MANIFEST.MF**: `VinaBookStore/.jpb/persistence-units.xml` (IDE JPA Buddy) và `VinaBookStore/src/main/java/META-INF/MANIFEST.MF` đều bị Git track — artifact không nên nằm trong source tree → P2.1, P2.2.
7. **Tên MartfuryShop còn sót**: đúng 5 vị trí (pom.xml, MartfuryShopApplicationTests.java, SendMailUtil.java, database.sql, angular-admin upload.service.ts) — bằng chứng `rg -i martfury` → P2.3.
8. **Admin common/\*.ts PascalCase**: 14 file liệt kê ở P1.3; đây cũng là nguồn gốc cảnh báo trong docs/architecture.md về storefront spec import folder `common/` không tồn tại bên storefront.
9. **helper/AuthInterceptor.ts**: interceptor JWT của admin — logic đúng (gắn Bearer từ SessionService cho API backend) nhưng vị trí/tên lệch chuẩn Angular → P1.4.
10. **soldest**: component + route + spec tồn tại tại `angular-admin/src/app/admin/soldest/` → P1.2.
11. **CSS rỗng**: 8 file 0 byte (danh sách ở P2.4).
12. **sb-admin-2.min.css/js**: nằm ở `angular-admin/src/assets/` → P2.5.
13. **Phân chia core/shared/services/models của storefront**: ranh giới core/shared chuẩn Angular; hai điểm chưa gọn: `shared/fly-to-cart.service.ts` (service trong thư mục UI, P2.6) và `models/common/` trùng tên với `common/` của admin dễ nhầm. Nhìn chung **nên giữ** cấu trúc hiện tại.
14. **Hardcoded localhost/credential demo**: chi tiết ở P0.2/P2.7 — nghiêm trọng nhất là JWT secret default còn giá trị fallback trong application.properties; các URL localhost đều dạng `${ENV:default}` nên chấp nhận được cho dev.
15. **Thiếu CI và lệnh kiểm tra toàn monorepo**: `.github/` chỉ chứa tool hook modernize/java-upgrade, không có `workflows/`; AGENTS.md chỉ liệt kê lệnh từng app riêng lẻ → P1.5 (CI) và Phase 4 (script kiểm tra một-lệnh).

## 6. Bản đồ đề xuất

Dạng `current/path -> proposed/path` (chỉ áp dụng khi Phase tương ứng được duyệt):

```
.gitmodules (không tồn tại)        -> .gitmodules                          # hoặc bỏ gitlink, gộp monorepo (B1)
bookstore-ui                       -> bookstore-ui/                        # giữ, thành directory thường nếu chọn monorepo
angular-admin                      -> angular-admin/                       # như trên
database.sql                        -> db/01-schema-seed.sql               # Phase 1
product_catalog_refresh.sql         -> db/02-product-catalog-refresh.sql   # Phase 1
category_data_fix.sql               -> db/03-category-data-fix.sql         # Phase 1
product57_category_fix.sql          -> db/04-product57-category-fix.sql    # đã di chuyển; script chỉ CHẠY sau khi chủ dự án duyệt
db/README.md                       -> db/README.md (mới)                   # thứ tự áp dụng + idempotency
VinaBookStore/.jpb/                -> (bỏ track, ignore)                   # Phase 3
VinaBookStore/src/main/java/META-INF/MANIFEST.MF
                                   -> (bỏ track, Maven tự sinh)            # Phase 3
VinaBookStore/src/main/java/vn/fs/api/OderDetailApi.java
                                   -> .../api/OrderDetailApi.java          # Phase 3
VinaBookStore/src/test/java/vn/fs/MartfuryShopApplicationTests.java
                                   -> .../test/.../VinaBookStoreApplicationTests.java  # Phase 3
angular-admin/src/app/helper/AuthInterceptor.ts
                                   -> angular-admin/src/app/core/interceptors/auth.interceptor.ts  # Phase 2
angular-admin/src/app/common/*.ts  -> angular-admin/src/app/models/*.ts (kebab-case)  # Phase 2
angular-admin/src/app/admin/soldest/
                                   -> angular-admin/src/app/admin/bestseller/ (+ redirect route)  # Phase 2
angular-admin/src/app/admin/*/*.component.css (8 file rỗng)
                                   -> (xóa + bỏ styleUrls)                 # Phase 2
.github/workflows/ci.yml           -> .github/workflows/ci.yml (mới)       # Phase 4
scripts/check-all.ps1              -> scripts/check-all.ps1 (mới)          # Phase 4
bookstore-ui/src/app/shared/fly-to-cart.service.ts
                                   -> bookstore-ui/src/app/services/fly-to-cart.service.ts  # Phase 4
```

## 7. Cấu trúc repo đích tối giản

Hai phương án cho Phase 0 (chọn một — xem Blocker B1):

**Phương án A — monorepo đơn (khuyến nghị cho quy mô hiện tại):**

```
vinabook/
├── .github/workflows/ci.yml        # build+test storefront / admin / backend
├── scripts/check-all.ps1|sh        # một lệnh chạy đủ 3 bộ kiểm tra
├── db/                             # 01-schema-seed.sql + data-fix đánh số theo thứ tự + README.md
├── docs/                           # giữ nguyên + bổ sung architecture (api/ vs controller/)
├── bookstore-ui/                   # directory thường trong repo cha
├── angular-admin/                  # directory thường trong repo cha
└── VinaBookStore/                  # không còn .jpb, META-INF/MANIFEST.MF
```

**Phương án B — ba repo riêng có submodule đúng chuẩn:** giữ gitlink, thêm
`.gitmodules` trỏ tới remote thật của từng app; repo cha chỉ chứa `db/`, `docs/`,
`scripts/`, CI tổng hợp.

## 8. Các phase thực hiện độc lập

- **Phase 0 — Git/monorepo (P0.1):** quyết định phương án A/B; tạo remote backup trước;
  nếu A: `git rm --cached bookstore-ui angular-admin`, bỏ `.git` con (sau khi hợp nhất
  lịch sử hoặc chấp nhận khởi tạo mới), track file trực tiếp; nếu B: viết `.gitmodules`.
- **Phase 1 — root, SQL và artifact chắc chắn (P0.2, P0.3):** tạo `db/` di chuyển 3–4 file
  SQL + README thứ tự; bỏ JWT secret default khỏi application.properties (yêu cầu biến môi
  trường); cập nhật .gitignore (.jpb).
- **Phase 2 — admin (P1.2–P1.4, P2.4, P2.5):** rename soldest→bestseller (+redirect);
  common/*.ts → models kebab-case; helper/AuthInterceptor.ts → core/interceptors/;
  xóa 8 CSS rỗng; ghi nhận kế hoạch sb-admin-2.
- **Phase 3 — backend (P1.1, P2.1–P2.3):** OderDetailApi → OrderDetailApi; untrack .jpb +
  MANIFEST.MF; đổi tên MartfuryShopApplicationTests/artifactId/text mail.
- **Phase 4 — scripts, CI và docs (P1.5, P2.6):** thêm `.github/workflows/ci.yml`
  (3 job), `scripts/check-all.ps1`; chuyển fly-to-cart.service.ts về services/; cập nhật
  AGENTS.md/docs với cấu trúc mới.

Mỗi phase độc lập, có thể merge/review riêng, không phụ thuộc phase khác ngoài Phase 0.

## 9. Test matrix cho từng phase

| Phase | Kiểm tra bắt buộc | Tiêu chí đạt |
|---|---|---|
| Phase 0 | `git ls-files -s` hai thư mục; clone thử vào thư mục tạm; `npm run build` storefront/admin; `mvnw test` | Không còn gitlink mồ côi (hoặc .gitmodules hợp lệ); clone mới build được cả 3 app |
| Phase 1 | Import `db/01-schema-seed.sql` vào MySQL dev sạch; chạy lần lượt theo thứ tự `db/02-product-catalog-refresh.sql` → `db/03-category-data-fix.sql` như `db/README.md`; `db/04-product57-category-fix.sql` chỉ chạy khi chủ dự án duyệt; verify query từng script; `mvnw test` với JWT_SECRET set; khởi động thiếu env phải fail-fast | Dữ liệu khớp kỳ vọng verify query; app chạy được với env đúng |
| Phase 2 | `ng build` admin; `ng test --watch=false`; đăng nhập admin → request có Bearer header; click menu bestseller; `rg soldest` = 0 (trừ redirect); `rg "common/"` admin = 0 import cũ | Build/test xanh; mọi route/menu hoạt động; không còn file PascalCase/rỗng |
| Phase 3 | `rg OderDetail` = 0; `rg -i martfury` = 0 (trừ history); `mvnw test`; `mvnw package` + `java -jar` smoke; `git status` không còn .jpb/MANIFEST | API order detail hoạt động như cũ; jar chạy được |
| Phase 4 | Push branch → CI xanh 3 job; `scripts/check-all.ps1` thoát 0; `ng build` storefront sau move fly-to-cart; smoke UI thêm giỏ hàng (animation vẫn chạy) | CI tự động chạy trên PR; một lệnh kiểm tra toàn monorepo |

## 10. Blocker cần chủ dự án quyết định

- **B1 — Mô hình repository (chặn Phase 0 và mọi việc remote/CI):** chọn monorepo đơn
  (Phương án A) hay 3 repo + submodule đúng chuẩn (Phương án B). Cần biết GitHub org/repo
  đích sẽ dùng. Trạng thái remote đã kiểm chứng: repo gốc và bookstore-ui KHÔNG có remote;
  angular-admin có `origin` → `https://github.com/ThanhLamm/veggie-shop-admin.git`.
  Rủi ro mất source (riêng repo gốc + bookstore-ui) là lý do mục này đứng đầu.
- **B2 — Secret dev đã lộ trong Git:** JWT secret default trong application.properties
  đã nằm trong lịch sử commit; dù bỏ default thì secret cũ vẫn cần bị coi là đã lộ và xoay.
  Chủ dự án cần xác nhận secret đó chỉ dùng local.
- **B3 — Thứ tự ưu tiên phase:** Phase 1 (SQL/secret) nên đi trước Phase 2/3 (rename)
  vì rủi ro cao hơn, nhưng cần chủ dự án phê duyệt lịch sử dừng tính năng để làm nền tảng.
- **B4 — db/04-product57-category-fix.sql:** tiếp tục chờ duyệt chạy trên MySQL dev (blocker từ
  phiên trước, ngoài phạm vi phiên audit này).

*— Hết tài liệu audit. Tài liệu chỉ mô tả trạng thái và đề xuất; chưa có thay đổi nào
được áp dụng lên code/config/file hiện có.*

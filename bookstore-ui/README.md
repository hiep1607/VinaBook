# VinaBook Storefront

Website khách hàng của VinaBook, xây bằng Angular 12.1 và SCSS.

## Chạy local

Backend phải chạy tại `http://localhost:8080`. `ng serve` dùng `proxy.conf.json` để chuyển `/api` sang backend.

```powershell
npm install
npm start
```

Mở `http://localhost:4200`.

## Kiểm tra

```powershell
npm run build
npx tsc -p tsconfig.spec.json --noEmit
npm test
```

## Cấu trúc

- `src/app/pages/`: các trang theo route.
- `src/app/components/`: header, footer, product card, chatbot.
- `src/app/services/`: API và state trình duyệt.
- `src/app/core/`: guard/interceptor.
- `src/app/shared/`: toast và directive dùng chung.
- `src/environments/`: cấu hình URL theo môi trường.

Production build dùng API cùng origin; hosting phải proxy `/api` tới Spring Boot và fallback route Angular về `index.html`.

Tài liệu đầy đủ nằm ở [`../docs/`](../docs/).

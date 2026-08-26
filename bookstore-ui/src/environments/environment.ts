export const environment = {
  production: false,
  // Để rỗng → mọi lời gọi API là tương đối ('/api/...'), đi qua proxy của ng serve
  // (xem proxy.conf.json) tới backend localhost:8080. Nhờ vậy frontend + API chung
  // một origin → chia sẻ public qua MỘT tunnel ngrok mà không vướng CORS.
  apiUrl: '',
  // Link mạng xã hội chính thức của VinaBook. Chỉ điền URL TÀI KHOẢN THẬT.
  // Khi mảng rỗng (mặc định), footer ẩn hẳn khu "Kết nối" — không render link placeholder.
  // Ví dụ: { label: 'Facebook', url: 'https://facebook.com/vinabook.chinh-thuc' }
  socialLinks: [] as Array<{ label: string; url: string }>
};

export const environment = {
  production: true,
  // Serve admin behind the same HTTPS origin and proxy these paths to Spring Boot.
  apiUrl: '',
  wsUrl: '/notification',
  storefrontUrl: '/',
  // Production KHÔNG hardcode tài khoản Cloudinary legacy. Điền URL upload của
  // dự án (unsigned preset riêng) tại build time nếu cần tính năng upload.
  cloudinaryUploadUrl: ''
};

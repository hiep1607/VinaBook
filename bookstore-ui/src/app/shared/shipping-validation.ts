export const SHIPPING_LIMITS = { name: 80, street: 255 };
export function normalizePhone(value: string): string {
  const phone = (value || '').replace(/[\s.()-]/g, '');
  return phone.startsWith('+84') ? '0' + phone.slice(3) : phone;
}
export function shippingError(name: string, phone: string, street: string): string {
  if (!(name || '').trim() || name.trim().length > SHIPPING_LIMITS.name) return 'Tên người nhận cần từ 1 đến 80 ký tự.';
  if (!/^0\d{9}$/.test(normalizePhone(phone))) return 'Số điện thoại cần đủ 10 chữ số, bắt đầu bằng 0 hoặc dùng mã +84.';
  if (!(street || '').trim() || street.trim().length < 5 || street.trim().length > SHIPPING_LIMITS.street) return 'Địa chỉ giao hàng cần từ 5 đến 255 ký tự.';
  return '';
}

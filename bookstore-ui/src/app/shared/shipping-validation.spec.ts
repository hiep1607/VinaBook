import { normalizePhone, shippingError } from './shipping-validation';
describe('Shared shipping validation', () => {
  it('accepts saved addresses and checkout using the same 255 character limit', () => {
    expect(shippingError('Người nhận', '+84 912-345-678', 'A'.repeat(255))).toBe('');
    expect(normalizePhone('+84 912-345-678')).toBe('0912345678');
    expect(shippingError('Người nhận', '0912345678', 'A'.repeat(256))).toContain('255');
  });
  it('rejects invalid phone numbers before saving or quoting', () => {
    expect(shippingError('Người nhận', '123456', '12 Đường mới')).toContain('điện thoại');
    expect(shippingError('Người nhận', '0912345678', 'abc')).toContain('Địa chỉ');
  });
});

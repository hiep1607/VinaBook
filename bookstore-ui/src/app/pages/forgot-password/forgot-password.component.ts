import { Component } from '@angular/core';

import { AuthService } from 'src/app/services/auth.service';

/**
 * Trang yêu cầu đặt lại mật khẩu.
 * - Gọi POST /api/auth/send-mail-forgot-password-token (permitAll).
 * - Backend luôn trả 200 bất kể email có tồn tại → thông báo phải trung tính,
 *   KHÔNG tiết lộ email có trong hệ thống hay không.
 * - Link đặt mật khẩu mới nằm trong email và trỏ tới trang backend
 *   /forgot-password/{token}; trang này chỉ gửi yêu cầu.
 */
@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {

  email = '';

  loading = false;

  /** Đã gửi yêu cầu thành công (hoặc backend xác nhận nhận yêu cầu). */
  sent = false;

  /** Lỗi mạng/kỹ thuật — không liên quan đến tính tồn tại của email. */
  submitError = '';

  private readonly emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  constructor(private authService: AuthService) {}

  get emailValid(): boolean {
    return this.emailPattern.test(this.email.trim());
  }

  get showEmailHint(): boolean {
    return !!this.email.trim() && !this.emailValid;
  }

  request(): void {
    if (this.loading || this.sent || !this.emailValid) {
      return;
    }

    this.loading = true;
    this.submitError = '';

    this.authService.requestPasswordReset(this.email.trim()).subscribe(ok => {
      this.loading = false;

      if (ok) {
        this.sent = true;
      } else {
        this.submitError = 'Không gửi được yêu cầu. Vui lòng kiểm tra kết nối và thử lại.';
      }
    });
  }

}

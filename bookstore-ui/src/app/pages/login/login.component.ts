import { Component } from '@angular/core';

import { Router } from '@angular/router';

import { AuthService } from 'src/app/services/auth.service';

import { NotificationService } from 'src/app/services/notification.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {

  email = '';

  password = '';

  errorMessage = '';

  /** Trạng thái hiện/ẩn mật khẩu trên ô nhập. */
  showPassword = false;

  /** Chống double-submit khi đang chờ backend. */
  loggingIn = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private notification: NotificationService,
    private route: ActivatedRoute
  ) {}

  toggleShowPassword(): void {
    this.showPassword = !this.showPassword;
  }

  login() {

    if (this.loggingIn) {
      return;
    }

    this.errorMessage = '';
    this.loggingIn = true;

    this.authService
      .login(this.email, this.password)
      .subscribe(success => {

        this.loggingIn = false;

        if (success) {

          this.notification.success(
            'Đăng nhập thành công'
          );

          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/';
          this.router.navigateByUrl(returnUrl.startsWith('/') ? returnUrl : '/');

        } else {

          this.errorMessage =
            'Sai tài khoản hoặc mật khẩu';

        }

      });

  }

}

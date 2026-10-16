import { Component } from '@angular/core';

import { Router } from '@angular/router';

import { AuthService } from 'src/app/services/auth.service';

import { NotificationService } from 'src/app/services/notification.service';
import { ActivatedRoute } from '@angular/router';
import { CartService } from 'src/app/services/cart.service';
import { ProductService } from 'src/app/services/product.service';
import { ChatCheckoutService } from 'src/app/services/chat-checkout.service';
import { timeout } from 'rxjs/operators';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {

  email = '';

  password = '';

  errorMessage = '';
  loading = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private notification: NotificationService,
    private route: ActivatedRoute,
    private cart: CartService,
    private products: ProductService,
    private purchaseIntent: ChatCheckoutService
  ) {}

  login() {
    if (this.loading) return;
    this.loading = true;

    this.errorMessage = '';

    this.authService
      .login(this.email, this.password)
      .pipe(timeout(20000)).subscribe(async success => {

        if (success) {

          this.notification.success(
            'Đăng nhập thành công'
          );

          const intent = this.purchaseIntent.consumePurchaseIntent('web');
          if (intent) {
            try {
              const owner = this.authService.currentUser?.email;
              const catalog = await this.products.refresh().pipe(timeout(20000)).toPromise();
              if (owner !== this.authService.currentUser?.email) return;
              const product = catalog.find(book => book.id === intent.productId);
              if (!product || product.quantity < intent.quantity) throw new Error('Sách không còn đủ số lượng đã chọn. Bạn kiểm tra lại nhé.');
              if (intent.action === 'ADD_TO_CART') {
                await this.cart.flush();
                if (owner !== this.authService.currentUser?.email) return;
                this.cart.addToCart(product, intent.quantity); await this.cart.flush();
                if (owner === this.authService.currentUser?.email) this.router.navigateByUrl('/cart');
              } else { this.cart.setBuyNowItem(product, intent.quantity); this.router.navigateByUrl('/checkout'); }
            } catch (error) {
              this.notification.error((error as any)?.message || 'Chưa khôi phục được lựa chọn mua. Bạn kiểm tra giỏ nhé.');
              this.router.navigate(['/product', intent.productId], { queryParams: { quantity: intent.quantity } });
            } finally { this.loading = false; }
            return;
          }
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/';
          this.loading = false;
          this.router.navigateByUrl(/^\/(?!\/)/.test(returnUrl) && !/[\\\u0000-\u001f]/.test(returnUrl) ? returnUrl : '/');

        } else {
          this.loading = false;

          this.errorMessage =
            'Sai tài khoản hoặc mật khẩu';

        }

      }, () => { this.loading = false; this.errorMessage = 'Chưa kết nối được máy chủ. Bạn thử lại nhé.'; });

  }

}

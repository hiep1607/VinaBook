import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from 'src/app/services/auth.service';
import { NotificationService } from 'src/app/services/notification.service';
import { OrderService } from 'src/app/services/order.service';
import { PaymentService } from 'src/app/services/payment.service';

@Component({
  selector: 'app-order-detail',
  templateUrl: './order-detail.component.html',
  styleUrls: ['./order-detail.component.scss']
})
export class OrderDetailComponent implements OnInit, OnDestroy {

  order: any = null;

  loading = true;
  loadError = false;
  notFound = false;
  paying = false;

  /** Các bước tiến trình đơn hàng; statusCode 3 = đã hủy, hiển thị riêng. */
  readonly timelineSteps = [
    { statusCode: 0, label: 'Chờ xử lý', description: 'Đơn hàng đã được ghi nhận' },
    { statusCode: 1, label: 'Đang giao', description: 'Sách đang trên đường đến bạn' },
    { statusCode: 2, label: 'Hoàn thành', description: 'Giao hàng thành công' }
  ];

  private subscriptions = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderService: OrderService,
    private authService: AuthService,
    private paymentService: PaymentService,
    private notification: NotificationService
  ) {}

  ngOnInit(): void {

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }

    // Subscribe paramMap để điều hướng giữa các đơn không cần tạo lại component
    this.subscriptions.add(this.route.paramMap.subscribe(params => {
      this.loadOrder(Number(params.get('id')));
    }));

  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get isCancelled(): boolean {
    return !!this.order && this.order.statusCode === 3;
  }

  /** Bước hiện tại trong timeline (0..2). Đơn hủy không dùng. */
  get currentStep(): number {
    if (!this.order || this.isCancelled) {
      return -1;
    }
    return Math.min(Math.max(this.order.statusCode, 0), this.timelineSteps.length - 1);
  }

  /** Tạm tính = tổng dòng sản phẩm. */
  get subtotal(): number {
    if (!this.order) {
      return 0;
    }
    return this.order.items.reduce(
      (sum: number, item: any) => sum + Number(item.price) * item.quantity,
      0
    );
  }

  /** Phí giao hàng suy ra từ tổng đơn backend (nguồn sự thật), không tính lại rule. */
  get shippingFee(): number {
    if (!this.order) {
      return 0;
    }
    return Math.max(Number(this.order.total) - this.subtotal, 0);
  }

  statusClass(status: string): string {
    const value = (status || '').toLowerCase();
    if (value.includes('hủy') || value.includes('cancel')) { return 'cancelled'; }
    if (value.includes('hoàn') || value.includes('success')) { return 'completed'; }
    if (value.includes('giao') || value.includes('deliver')) { return 'shipping'; }
    return 'pending';
  }

  paymentMethodLabel(method: string): string {
    switch (method) {
      case 'BANK_QR': return 'Chuyển khoản QR';
      case 'VNPAY': return 'VNPay';
      case 'MOMO': return 'MoMo';
      default: return 'Thanh toán khi nhận hàng';
    }
  }

  paymentStatusLabel(order: any): string {
    if (order.paymentStatus === 'PAID') { return 'Đã thanh toán'; }
    if (order.paymentStatus === 'FAILED') { return 'Chưa hoàn tất'; }
    if (order.paymentMethod === 'COD') { return 'Thanh toán khi nhận hàng'; }
    return 'Chờ thanh toán';
  }

  canPay(order: any): boolean {
    return order.statusCode !== 2 && order.statusCode !== 3
      && order.paymentMethod !== 'COD' && order.paymentStatus !== 'PAID';
  }

  continuePayment(): void {
    const order = this.order;
    if (!this.canPay(order) || this.paying) { return; }

    if (order.paymentMethod === 'BANK_QR' && order.paymentStatus === 'PENDING') {
      this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } });
      return;
    }

    this.paying = true;
    this.paymentService.start(order.id).subscribe(
      payment => {
        this.paying = false;
        if (payment.redirectUrl) {
          window.location.assign(payment.redirectUrl);
          return;
        }
        this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } });
      },
      error => {
        this.paying = false;
        const message = error && error.error && error.error.message
          ? error.error.message : 'Không thể mở thanh toán';
        this.notification.error(message);
      }
    );
  }

  private loadOrder(id: number): void {

    if (!id || Number.isNaN(id)) {
      this.notFound = true;
      this.loading = false;
      return;
    }

    this.loading = true;
    this.loadError = false;
    this.notFound = false;
    this.order = null;

    this.orderService.getOrder(this.authService.currentUser.email, id).subscribe(
      order => {
        this.order = order;
        this.loading = false;
      },
      () => {
        this.loading = false;
        this.loadError = true;
      }
    );

  }

}

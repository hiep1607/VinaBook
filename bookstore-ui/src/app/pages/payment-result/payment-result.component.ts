import { Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, timer } from 'rxjs';
import { take, timeout } from 'rxjs/operators';
import { AuthService } from 'src/app/services/auth.service';
import { NotificationService } from 'src/app/services/notification.service';
import { PaymentService, PaymentState } from 'src/app/services/payment.service';

@Component({ selector: 'app-payment-result', templateUrl: './payment-result.component.html', styleUrls: ['./payment-result.component.scss'] })
export class PaymentResultComponent implements OnInit, DoCheck, OnDestroy {
  orderId = 0;
  payment: PaymentState | null = null;
  loading = true;
  checking = false;
  retrying = false;
  loadError = '';
  pollingPaused = false;
  qrFailed = false;
  lastChecked: Date | null = null;
  private owner = '';
  private version = 0;
  private destroyed = false;
  private pollCount = 0;
  private routeSubscription?: Subscription;
  private pollSubscription?: Subscription;
  private statusSubscription?: Subscription;
  private paySubscription?: Subscription;

  constructor(private route: ActivatedRoute, private router: Router,
    private paymentService: PaymentService, private notification: NotificationService,
    private auth: AuthService) {}

  ngOnInit(): void {
    this.owner = this.auth.currentUser?.email || '';
    if (!this.owner || !this.auth.isLoggedIn()) { this.leaveSession(); return; }
    this.routeSubscription = this.route.queryParamMap.subscribe(params => {
      this.reset();
      this.orderId = Number(params.get('orderId'));
      if (!Number.isSafeInteger(this.orderId) || this.orderId < 1) {
        this.loading = false;
        this.loadError = 'Không tìm thấy mã đơn hàng hợp lệ. Bạn mở lại từ mục Đơn hàng nhé.';
        return;
      }
      this.startPolling();
    });
  }
  ngDoCheck(): void {
    if (this.owner && this.owner !== (this.auth.currentUser?.email || '')) this.leaveSession();
  }
  ngOnDestroy(): void {
    this.destroyed = true;
    this.reset();
    this.routeSubscription?.unsubscribe();
  }
  private current(version: number): boolean {
    return !this.destroyed && version === this.version && !!this.owner && this.owner === this.auth.currentUser?.email;
  }
  private reset(): void {
    this.version++;
    this.pollSubscription?.unsubscribe(); this.statusSubscription?.unsubscribe(); this.paySubscription?.unsubscribe();
    this.payment = null; this.loading = true;
    this.checking = this.retrying = this.pollingPaused = this.qrFailed = false;
    this.loadError = ''; this.lastChecked = null;
  }
  private leaveSession(): void {
    this.reset(); this.owner = ''; this.loading = false;
    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }
  get terminal(): boolean {
    return !!this.payment && (this.payment.paymentStatus === 'PAID'
      || this.payment.paymentMethod === 'COD' || [2, 3].includes(this.payment.orderStatus));
  }
  get canPay(): boolean {
    return !!this.payment && !this.terminal && this.payment.canStart !== false && this.payment.methodAvailable !== false;
  }
  startPolling(): void {
    this.pollSubscription?.unsubscribe();
    if (!Number.isSafeInteger(this.orderId) || this.orderId < 1 || !this.current(this.version)) return;
    this.pollingPaused = false; this.pollCount = 0;
    this.pollSubscription = timer(0, 8000).subscribe(() => {
      // A polling timeout does not mean that the payment failed.
      if (this.pollCount++ >= 38) { this.pollingPaused = true; this.pollSubscription?.unsubscribe(); return; }
      this.loadStatus();
    });
  }
  loadStatus(): void {
    if (!this.current(this.version) || !Number.isSafeInteger(this.orderId) || this.orderId < 1 || this.checking || this.retrying) return;
    this.checking = true;
    const version = this.version;
    this.statusSubscription = this.paymentService.getStatus(this.orderId).pipe(take(1), timeout(12000)).subscribe({
      next: payment => {
        if (!this.current(version)) return;
        this.checking = this.loading = false; this.loadError = '';
        this.payment = payment; this.lastChecked = new Date();
        if (this.terminal || payment.paymentStatus === 'FAILED' || payment.canPoll === false) this.pollSubscription?.unsubscribe();
      },
      error: error => {
        if (!this.current(version)) return;
        this.checking = this.loading = false; this.pollSubscription?.unsubscribe();
        if (error?.status === 401) { this.leaveSession(); return; }
        if ([403, 404].includes(error?.status)) { this.payment = null; this.loadError = 'Không tìm thấy đơn này trong tài khoản của bạn.'; return; }
        this.pollingPaused = true;
        this.loadError = 'Chưa cập nhật được trạng thái. Nếu đã thanh toán, bạn đợi xác nhận trước khi thử lại nhé.';
      }
    });
  }
  retryPayment(): void {
    if (!this.canPay || this.retrying || this.checking || !this.current(this.version)) return;
    this.retrying = true; this.pollSubscription?.unsubscribe();
    const version = this.version;
    this.paySubscription = this.paymentService.start(this.orderId).pipe(take(1), timeout(25000)).subscribe({
      next: result => {
        if (!this.current(version)) return;
        this.retrying = false;
        if (result.redirectUrl) { window.location.assign(result.redirectUrl); return; }
        this.payment = result; this.loadError = '';
        if (!this.terminal) this.startPolling();
      },
      error: error => {
        if (!this.current(version)) return;
        this.retrying = false;
        if (error?.status === 401) { this.leaveSession(); return; }
        this.notification.error(error?.error?.message || 'Chưa mở được thanh toán. Đơn của bạn vẫn được giữ trong mục Đơn hàng.');
        this.loadStatus();
      }
    });
  }
  copy(value: string | number | undefined, label: string): void {
    if (value === undefined || value === '') return;
    const text = String(value);
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(() => this.notification.success('Đã sao chép ' + label),
        () => this.notification.info('Bạn sao chép thủ công ' + label + ': ' + text));
    } else this.notification.info(label + ': ' + text);
  }
  methodLabel(): string {
    return ({ BANK_QR: 'Chuyển khoản VietQR', VNPAY: 'VNPay Sandbox', MOMO: 'MoMo Test', COD: 'Thanh toán khi nhận hàng' })[this.payment?.paymentMethod || 'COD'];
  }
  goToOrder(): void { if (this.orderId > 0) this.router.navigate(['/orders', this.orderId]); }
  goToOrders(): void { this.router.navigate(['/orders']); }
}

import { Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { NotificationService } from 'src/app/services/notification.service';
import { CustomerOrder, OrderService } from 'src/app/services/order.service';
import { PaymentService } from 'src/app/services/payment.service';

@Component({ selector: 'app-order-detail', templateUrl: './order-detail.component.html', styleUrls: ['./order-detail.component.scss'] })
export class OrderDetailComponent implements OnInit, DoCheck, OnDestroy {
  order: CustomerOrder | null = null;
  loading = true; loadError = false; notFound = false; paying = false;
  activeAction = ''; actionBusy = false;
  readonly timelineSteps = [
    { statusCode: 0, label: 'Chờ xử lý', description: 'Đơn hàng đã được ghi nhận' },
    { statusCode: 1, label: 'Đang giao', description: 'Sách đang trên đường đến bạn' },
    { statusCode: 2, label: 'Hoàn thành', description: 'Giao hàng thành công' }
  ];
  private owner = ''; private orderId = 0; private version = 0;
  private subscriptions = new Subscription(); private loadSub?: Subscription; private paySub?: Subscription;
  constructor(private route: ActivatedRoute, private router: Router, public orderService: OrderService,
    private authService: AuthService, private paymentService: PaymentService, private notification: NotificationService) {}
  ngOnInit(): void {
    this.owner = this.authService.currentUser?.email || '';
    if (!this.authService.isLoggedIn()) { this.login(); return; }
    this.subscriptions.add(this.route.paramMap.subscribe(params => {
      this.orderId = Number(params.get('id')); this.reload();
    }));
  }
  ngDoCheck(): void {
    if (this.owner && this.owner !== (this.authService.currentUser?.email || '')) {
      this.clear(); this.owner = ''; this.login();
    }
  }
  ngOnDestroy(): void { this.clear(); this.subscriptions.unsubscribe(); }
  private login(): void { this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } }); }
  private clear(): void {
    this.version++; this.loadSub?.unsubscribe(); this.paySub?.unsubscribe();
    this.order = null; this.activeAction = ''; this.actionBusy = false; this.paying = false;
  }
  reload(): void {
    this.clear(); this.loading = false; this.loadError = false; this.notFound = false;
    if (!Number.isSafeInteger(this.orderId) || this.orderId < 1) { this.notFound = true; return; }
    this.loading = true; const version = this.version;
    this.loadSub = this.orderService.getOrder(this.orderId).subscribe({
      next: order => { if (this.current(version)) { this.order = order; this.loading = false; } },
      error: error => {
        if (!this.current(version)) return;
        this.loading = false; this.notFound = [403,404].includes(error.status); this.loadError = !this.notFound;
        if (error.status === 401) this.login();
      }
    });
  }
  private current(version: number): boolean { return version === this.version && !!this.owner && this.owner === this.authService.currentUser?.email; }
  get isCancelled(): boolean { return this.order?.statusCode === 3; }
  get currentStep(): number { return this.order && [0,1,2].includes(this.order.statusCode) ? this.order.statusCode : -1; }
  get subtotal(): number { return this.order?.items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0) || 0; }
  get shippingFee(): number { return this.order ? (this.order.shippingFee ?? Math.max(Number(this.order.total) + (this.order.discount || 0) - this.subtotal, 0)) : 0; }
  statusClass(status: string): string {
    if (status.includes('hủy')) return 'cancelled'; if (status.includes('Hoàn')) return 'completed';
    return status.includes('giao') ? 'shipping' : 'pending';
  }
  refundLabel(status:string):string {return ({SUCCEEDED:"Đã hoàn tiền",FAILED:"Không hoàn thành",PENDING:"Đang được xử lý",PROCESSING:"Đang gửi yêu cầu",UNKNOWN:"Cửa hàng đang đối soát"} as any)[status]||"Đang kiểm tra";}
  paymentMethodLabel(method: string): string {
    return ({ BANK_QR: 'Chuyển khoản QR', VNPAY: 'VNPay', MOMO: 'MoMo', COD: 'Thanh toán khi nhận hàng' } as {[key:string]:string})[method] || 'Không rõ';
  }
  paymentStatusLabel(order: CustomerOrder): string {
    if (order.paymentStatus === 'PAID') return 'Đã thanh toán';
    if (order.paymentStatus === 'FAILED') return 'Chưa hoàn tất';
    return order.paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng' : 'Chờ thanh toán';
  }
  canPay(order: CustomerOrder | null): boolean {
    return !!order && [0,1].includes(order.statusCode) && order.paymentMethod !== 'COD' && order.paymentStatus !== 'PAID';
  }
  continuePayment(): void {
    const order = this.order; if (!order || !this.canPay(order) || this.paying || this.actionBusy) return;
    if (order.paymentMethod === 'BANK_QR' && order.paymentStatus === 'PENDING') {
      this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } }); return;
    }
    this.paying = true; const version = this.version;
    this.paySub = this.paymentService.start(order.id).subscribe({
      next: payment => {
        if (!this.current(version)) return; this.paying = false;
        if (payment.redirectUrl) { window.location.assign(payment.redirectUrl); return; }
        this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } });
      },
      error: error => { if (this.current(version)) { this.paying = false; this.notification.error(error?.error?.message || 'Mình chưa mở được thanh toán. Bạn thử lại nhé.'); } }
    });
  }
  openAction(action: string): void { if (!this.actionBusy && !this.paying) this.activeAction = action; }
  cancelled(): void { this.reload(); }
}

import { AfterViewChecked, Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { orderStatusClass } from 'src/app/shared/order-presentation';
import { FabCollisionService } from 'src/app/services/fab-collision.service';

import { Router } from '@angular/router';

import { OrderService } from 'src/app/services/order.service';
import { AuthService } from 'src/app/services/auth.service';
import { PaymentService } from 'src/app/services/payment.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-orders',
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.scss']
})
export class OrdersComponent implements OnInit, DoCheck, OnDestroy, AfterViewChecked {

  orders: any[] = [];

  loading = true;
  loadError = false;
  payingOrderId = 0;
  cancellingOrderId = 0;
  cancellationBusy = false;
  reorderOrderId = 0;
  returnOrderId = 0;
  activeStatus = -1;
  query = '';
  page = 0;
  readonly pageSize = 20;
  total = 0;
  counts: {[status: string]: number} = {};
  private filterTimer?: ReturnType<typeof setTimeout>;
  get totalPages(): number { return Math.max(1, Math.ceil(this.total / this.pageSize)); }
  filterChanged(): void {
    if (this.cancellationBusy || this.payingOrderId) return;
    this.page = 0;
    if (this.filterTimer) clearTimeout(this.filterTimer);
    this.filterTimer = setTimeout(() => this.ngOnInit(), 250);
  }
  changePage(page: number): void {
    if (this.loading || this.cancellationBusy || this.payingOrderId || page < 0 || page >= this.totalPages) return;
    this.page = page; this.ngOnInit();
  }
  readonly statusTabs = [{ code: -1, label: 'Tất cả' }, { code: 0, label: 'Chờ xử lý' },
    { code: 1, label: 'Đang giao' }, { code: 2, label: 'Hoàn thành' }, { code: 3, label: 'Đã hủy' }];
  orderStatusClass = orderStatusClass;
  private request?: Subscription;
  private owner = '';
  private version = 0;
  private layoutKey = '';

  get filteredOrders(): any[] {
    const query = this.normalize(this.query).replace(/^#/, '');
    return this.orders.filter(order => (this.activeStatus < 0 || order.statusCode === this.activeStatus)
      && (!query || String(order.id).includes(query) || order.items?.some((item: any) => this.normalize(item.name).includes(query))));
  }
  countStatus(code: number): number { return this.counts[String(code)] ?? (code < 0 ? this.orders.length : this.orders.filter(order => order.statusCode === code).length); }
  itemCount(order: any): number { return (order.items || []).reduce((sum: number, item: any) => sum + item.quantity, 0); }
  clearFilters(): void { this.query = ''; this.activeStatus = -1; this.filterChanged(); }
  private normalize(text: string): string { return (text || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').trim(); }

  constructor(
    public orderService: OrderService,
    private authService: AuthService,
    private router: Router,
    private paymentService: PaymentService,
    private notification: NotificationService,
    private collision: FabCollisionService
  ) {}

  ngOnInit(): void {
    this.request?.unsubscribe();
    const version = ++this.version;
    this.owner = this.authService.currentUser?.email || '';
    const owner = this.owner;
    this.loading = true;
    this.loadError = false;

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.request = this.orderService
      .getOrderPage(this.page, this.pageSize, this.activeStatus, this.query)
      .subscribe(
        result => {
          if (version !== this.version || owner !== this.authService.currentUser?.email) return;
          this.orders = result.items;
          this.total = result.total; this.counts = result.counts;
          this.loading = false;
        },
        () => {
          if (version !== this.version || owner !== this.authService.currentUser?.email) return;
          this.orders = [];
          this.loading = false;
          this.loadError = true;
        }
      );

  }

  ngDoCheck(): void {
    if (this.owner === (this.authService.currentUser?.email || '')) return;
    this.version++; this.request?.unsubscribe(); this.orders = []; this.loading = false;
    this.cancellingOrderId = 0; this.reorderOrderId = 0; this.returnOrderId = 0;
    this.owner = this.authService.currentUser?.email || '';
    this.router.navigate(['/login']);
  }
  ngOnDestroy(): void { this.version++; this.request?.unsubscribe(); if (this.filterTimer) clearTimeout(this.filterTimer); }
  ngAfterViewChecked(): void {
    const key = [this.loading, this.query, this.activeStatus, this.filteredOrders.map(order => order.id).join(','),
      this.cancellingOrderId, this.reorderOrderId, this.returnOrderId].join(':');
    if (key !== this.layoutKey) { this.layoutKey = key; this.collision.recheck(); }
  }

  statusClass(status: string): string {
    const value = (status || '').toLowerCase();
    if (value.includes('hủy') || value.includes('cancel')) { return 'cancelled'; }
    if (value.includes('hoàn') || value.includes('success')) { return 'completed'; }
    if (value.includes('giao') || value.includes('deliver')) { return 'shipping'; }
    return 'pending';
  }

  requestCancellation(order: any): void {
    if (this.cancellationBusy || this.payingOrderId || !this.orderService.canCancel(order)) return;
    this.cancellingOrderId = order.id;
    this.reorderOrderId = 0;
    this.returnOrderId = 0;
  }

  orderCancelled(id: number): void {
    const order = this.orders.find(item => item.id === id);
    if (order) { order.statusCode = 3; order.status = this.orderService.statusLabel(3); }
    this.ngOnInit();
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

  continuePayment(order: any): void {
    if (!this.canPay(order) || this.payingOrderId || this.cancellationBusy) { return; }
    if (order.paymentMethod === 'BANK_QR' && order.paymentStatus === 'PENDING') {
      this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } });
      return;
    }
    this.payingOrderId = order.id;
    this.paymentService.start(order.id).subscribe(
      payment => {
        this.payingOrderId = 0;
        if (payment.redirectUrl) {
          window.location.assign(payment.redirectUrl);
          return;
        }
        this.router.navigate(['/payment/result'], { queryParams: { orderId: order.id } });
      },
      error => {
        this.payingOrderId = 0;
        const message = error && error.error && error.error.message
          ? error.error.message : 'Không thể mở thanh toán';
        this.notification.error(message);
      }
    );
  }

}

import { Component, DoCheck, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { OrderCancellationPreview, OrderService } from 'src/app/services/order.service';
import { OrderItemView } from '../order-items/order-items.component';

/** Shared confirmation for the Orders page and chatbot. Only a button submits cancellation. */
@Component({
  selector: 'app-order-cancellation',
  templateUrl: './order-cancellation.component.html',
  styleUrls: ['./order-cancellation.component.scss']
})
export class OrderCancellationComponent implements OnChanges, DoCheck, OnDestroy {
  @Input() orderId = 0;
  @Output() completed = new EventEmitter<number>();
  @Output() closed = new EventEmitter<void>();
  @Output() busyChange = new EventEmitter<boolean>(true);
  @Output() updated = new EventEmitter<void>();
  preview?: OrderCancellationPreview;
  busy = false;
  error = '';
  uncertain = false;
  done = false;
  private owner = '';
  private version = 0;
  private request?: Subscription;
  private detailsRequest?: Subscription;
  private detailItems: OrderItemView[] = [];
  get displayItems(): OrderItemView[] {
    return (this.preview?.items || []).map((item, index) => {
      const detail = this.detailItems[index];
      return detail?.name === item.name && detail.quantity === item.quantity ? { ...detail, ...item } : item;
    });
  }

  constructor(private orders: OrderService, private auth: AuthService) {}
  ngOnChanges(): void {
    this.owner = this.auth.currentUser?.email || '';
    this.version++;
    this.request?.unsubscribe();
    this.detailsRequest?.unsubscribe(); this.detailItems = [];
    this.setBusy(false);
    this.preview = undefined; this.done = false; this.uncertain = false; this.error = '';
    this.load();
  }
  ngDoCheck(): void {
    if (this.owner !== (this.auth.currentUser?.email || '')) {
      this.version++; this.request?.unsubscribe(); this.detailsRequest?.unsubscribe(); this.detailItems = []; this.preview = undefined;
      this.owner = this.auth.currentUser?.email || '';
      this.setBusy(false); this.closed.emit();
    }
  }
  private setBusy(value: boolean): void { this.busy = value; this.busyChange.emit(value); this.updated.emit(); }
  private current(version: number, owner: string): boolean {
    return this.version === version && owner === (this.auth.currentUser?.email || '');
  }
  load(): void {
    if (this.busy) return;
    if (!this.owner) { this.error = 'Bạn đăng nhập để mình kiểm tra đúng đơn của bạn và bảo vệ thông tin đơn hàng nhé.'; return; }
    if (!Number.isSafeInteger(this.orderId) || this.orderId < 1) { this.error = 'Mã đơn chưa hợp lệ. Bạn chọn lại đơn muốn hủy nhé.'; return; }
    const version = this.version, owner = this.owner;
    this.error = ''; this.setBusy(true);
    this.detailsRequest?.unsubscribe(); this.detailItems = [];
    this.detailsRequest = this.orders.getOrder(this.orderId).subscribe({
      next: order => { if (this.current(version, owner) && order.id === this.orderId) { this.detailItems = order.items; this.updated.emit(); } },
      error: () => { /* Image enrichment must not block the authoritative cancellation preview. */ }
    });
    this.request = this.orders.cancellationPreview(this.orderId).subscribe({
      next: preview => {
        if (!this.current(version, owner)) return;
        this.preview = preview; this.uncertain = false;
        if (preview.statusCode === 3) this.success(preview.orderId);
        this.setBusy(false);
      },
      error: error => {
        if (!this.current(version, owner)) return;
        this.error = error?.error?.message || 'Mình chưa tải được trạng thái đơn. Bạn thử kiểm tra lại khi kết nối ổn định nhé.';
        this.setBusy(false);
      }
    });
  }
  confirm(): void {
    if (this.busy || this.done || this.uncertain || !this.preview?.canCancel) return;
    const version = this.version, owner = this.owner, id = this.preview.orderId;
    if (!this.current(version, owner) || id !== this.orderId) return;
    this.error = ''; this.setBusy(true);
    this.request = this.orders.cancelOrder(id).subscribe({
      next: result => {
        if (!this.current(version, owner)) return;
        if (result.orderId === id && result.status === 'CANCELLED') this.success(id);
        else { this.uncertain = true; this.error = 'Mình chưa xác nhận được kết quả. Bạn kiểm tra lại trạng thái đơn nhé.'; }
        this.setBusy(false);
      },
      error: error => {
        if (!this.current(version, owner)) return;
        this.uncertain = true;
        this.error = error?.error?.message || 'Kết nối bị gián đoạn nên mình chưa biết đơn đã hủy chưa. Bạn kiểm tra trạng thái trước khi thử tiếp nhé.';
        this.setBusy(false);
      }
    });
  }
  private success(id: number): void {
    if (this.done) return;
    this.done = true; this.uncertain = false; this.completed.emit(id);
  }
  dismiss(): void { if (!this.busy) this.closed.emit(); }
  ngOnDestroy(): void { this.version++; this.request?.unsubscribe(); this.detailsRequest?.unsubscribe(); }
}

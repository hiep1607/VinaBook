import { Component, DoCheck, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from 'src/app/services/auth.service';
import { ChatAction } from 'src/app/services/chatbot.service';
import { CustomerOrder, OrderService } from 'src/app/services/order.service';
import { orderStatusClass, paymentLabel } from 'src/app/shared/order-presentation';

@Component({ selector: 'app-chat-orders', templateUrl: './chat-orders.component.html', styleUrls: ['./chat-orders.component.scss'] })
export class ChatOrdersComponent implements OnChanges, DoCheck, OnDestroy {
  @Input() orderIds: number[] = [];
  @Input() disabled = false;
  @Input() refreshKey = 0;
  @Output() action = new EventEmitter<ChatAction>();
  @Output() loaded = new EventEmitter<boolean>();
  @Output() updated = new EventEmitter<void>();
  orders: CustomerOrder[] = [];
  loading = false;
  error = '';
  statusClass = orderStatusClass;
  paymentLabel = paymentLabel;
  private owner = '';
  private version = 0;
  private key = '';
  private request?: Subscription;
  constructor(public service: OrderService, private auth: AuthService) {}
  ngOnChanges(): void {
    const key = this.orderIds.join(',') + ':' + this.refreshKey;
    if (key === this.key) return;
    this.key = key;
    this.load();
  }
  ngDoCheck(): void {
    if (this.owner !== (this.auth.currentUser?.email || '')) {
      this.version++; this.request?.unsubscribe(); this.orders = []; this.error = ''; this.loading = false;
      this.owner = this.auth.currentUser?.email || ''; this.loaded.emit(false);
    }
  }
  load(): void {
    this.request?.unsubscribe(); const version = ++this.version;
    const owner = this.owner = this.auth.currentUser?.email || '';
    this.orders = []; this.error = ''; this.loaded.emit(false);
    const ids = [...new Set(this.orderIds)].filter(id => Number.isSafeInteger(id) && id > 0).slice(0, 3);
    if (!owner || !ids.length) { this.loading = false; return; }
    this.loading = true;
    this.request = forkJoin(ids.map(id => this.service.getOrder(id).pipe(catchError(() => of(null))))).subscribe(results => {
      if (version !== this.version || owner !== this.auth.currentUser?.email) return;
      this.orders = results.filter((order): order is CustomerOrder => !!order);
      this.loading = false;
      if (this.orders.length < ids.length) this.error = 'Một số đơn chưa tải được. Bạn thử lại để xem thông tin mới nhất nhé.';
      this.loaded.emit(this.orders.length === ids.length); this.updated.emit();
    });
  }
  run(type: ChatAction['type'], order: CustomerOrder): void {
    if (this.disabled || this.loading || this.owner !== this.auth.currentUser?.email || !this.orders.includes(order)) return;
    if (type === 'CANCEL_ORDER' && !this.service.canCancel(order)) return;
    this.action.emit({ type, orderId: order.id, label: 'Đơn #' + order.id,
      route: type === 'VIEW_ORDER' ? '/orders/' + order.id : undefined,
      requiresConfirmation: type !== 'VIEW_ORDER' });
  }
  ngOnDestroy(): void { this.version++; this.request?.unsubscribe(); }
}

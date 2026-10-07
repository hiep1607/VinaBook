import { Component, DoCheck, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { timeout } from 'rxjs/operators';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { chatCartFingerprint } from 'src/app/services/chat-checkout.service';
import { OrderService } from 'src/app/services/order.service';
import { ProductService } from 'src/app/services/product.service';

@Component({ selector: 'app-order-reorder', templateUrl: './order-reorder.component.html', styleUrls: ['./order-reorder.component.scss'] })
export class OrderReorderComponent implements OnChanges, DoCheck, OnDestroy {
  @Input() orderId = 0;
  @Output() closed = new EventEmitter<void>();
  @Output() busyChange = new EventEmitter<boolean>(true);
  @Output() updated = new EventEmitter<void>();
  lines: { product?: any; name: string; quantity: number }[] = [];
  busy = false; error = ''; done = false; uncertain = false;
  private owner = ''; private version = 0; private cartKey = '';
  constructor(private orders: OrderService, private products: ProductService, private cart: CartService, private auth: AuthService) {}
  ngOnChanges(): void {
    this.version++; this.owner = this.auth.currentUser?.email || ''; this.lines = []; this.done = false; this.uncertain = false;
    this.setBusy(false); void this.load();
  }
  ngDoCheck(): void {
    if (this.owner !== (this.auth.currentUser?.email || '')) { this.version++; this.lines = []; this.owner = this.auth.currentUser?.email || ''; this.setBusy(false); this.closed.emit(); }
  }
  private current(version: number, owner: string): boolean { return version === this.version && owner === (this.auth.currentUser?.email || ''); }
  private setBusy(value: boolean): void { this.busy = value; this.busyChange.emit(value); this.updated.emit(); }
  private fingerprint(): string { return chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision); }
  get subtotal(): number { return this.lines.reduce((sum, line) => sum + (line.product?.price || 0) * line.quantity, 0); }
  get canAdd(): boolean { return !!this.lines.length && this.lines.every(line => !!line.product && Number.isInteger(line.quantity) && line.quantity > 0 && line.quantity <= Math.min(99, line.product.quantity)); }
  async load(): Promise<void> {
    if (this.busy) return;
    if (!this.owner) { this.error = 'Bạn đăng nhập để mình mở đúng đơn đã mua của bạn nhé.'; return; }
    const version = this.version, owner = this.owner; this.error = ''; this.setBusy(true);
    try {
      await this.cart.flush();
      const [source, catalog] = await Promise.all([this.orders.reorderPreview(this.orderId).toPromise(), this.products.refresh().pipe(timeout(20000)).toPromise()]);
      if (!this.current(version, owner)) return;
      this.lines = source.items.map(item => ({ name: item.name, quantity: item.quantity, product: catalog.find(book => book.id === item.productId) }));
      this.cartKey = this.fingerprint();
    } catch (error) { if (this.current(version, owner)) this.error = (error as any)?.error?.message || (error as any)?.message || 'Mình chưa tải được đơn cũ và giá hiện tại. Bạn thử lại nhé.'; }
    finally { if (this.current(version, owner)) this.setBusy(false); }
  }
  async confirm(): Promise<void> {
    if (this.busy || this.done || this.uncertain || !this.canAdd) return;
    const version = this.version, owner = this.owner; this.error = ''; this.setBusy(true);
    let submitted = false;
    try {
      await this.cart.flush();
      if (!this.current(version, owner)) return;
      if (this.cartKey !== this.fingerprint()) throw new Error('Giỏ đã thay đổi. Bạn tải lại trước khi thêm sách từ đơn cũ nhé.');
      this.cart.addBooks(this.lines.map(line => ({ product: line.product, quantity: line.quantity })));
      submitted = true; await this.cart.flush();
      if (!this.current(version, owner)) return;
      this.done = true;
    } catch (error) {
      if (this.current(version, owner)) { this.uncertain = submitted; this.error = (error as any)?.error?.message || (error as any)?.message || 'Mình chưa xác nhận được kết quả cập nhật giỏ. Bạn mở giỏ kiểm tra trước khi thêm lại nhé.'; }
    } finally { if (this.current(version, owner)) this.setBusy(false); }
  }
  dismiss(): void { if (!this.busy) this.closed.emit(); }
  ngOnDestroy(): void { this.version++; }
}

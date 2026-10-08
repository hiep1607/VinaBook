import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { StoreKnowledgeService } from './store-knowledge.service';
import { timeout } from 'rxjs/operators';

export interface CustomerOrder {
  id: number; date: string | null; total: number; statusCode: number; status: string;
  paymentMethod: string; paymentStatus: string; paidAt: string | null;
  discount?:number;voucherCode?:string;shippingFee?:number;
  refunds?: {refunded:number;refunds:{id:number;amount:number;provider:string;status:string;createdAt:string}[]};
  shippingAddress: { name: string; phone: string; street: string };
  items: { productId: number | null; name: string; image: string; quantity: number; price: number }[];
}

export interface OrderCancellationPreview {
  orderId: number;
  orderDate: string | null;
  total: number;
  statusCode: number;
  canCancel: boolean;
  reason: string;
  items: { name: string; quantity: number }[];
}

/** Đơn hàng server-side: quote tính giá/ship, confirm tạo đơn và giữ tồn kho. */
@Injectable({
  providedIn: 'root'
})
export class OrderService {

  private api = environment.apiUrl;

  constructor(private http: HttpClient, private knowledge: StoreKnowledgeService) { }

  getOrder(orderId: number): Observable<CustomerOrder> {
    return this.http.get<CustomerOrder>(this.api + '/api/orders/me/' + orderId).pipe(
      timeout(20000), map(order => ({ ...order, status: this.statusLabel(order.statusCode) }))
    );
  }

  cancellationPreview(orderId: number): Observable<OrderCancellationPreview> {
    return this.http.get<OrderCancellationPreview>(this.api + '/api/orders/me/' + orderId + '/cancellation').pipe(timeout(20000));
  }
  reorderPreview(orderId: number): Observable<{orderId: number; items: {productId: number | null; name: string; quantity: number}[]}> {
    return this.http.get<{orderId: number; items: {productId: number | null; name: string; quantity: number}[]}>(this.api + '/api/orders/me/' + orderId + '/reorder').pipe(timeout(20000));
  }

  cancelOrder(orderId: number): Observable<{ orderId: number; status: string }> {
    return this.http.post<{ orderId: number; status: string }>(this.api + '/api/orders/me/' + orderId + '/cancel', { confirmed: true }).pipe(timeout(20000));
  }

  canCancel(order: any): boolean {
    return order?.statusCode === 0 && (!order.paymentMethod || order.paymentMethod === 'COD') && order.paymentStatus !== 'PAID';
  }

  statusLabel(status: number): string {
    switch (status) {
      case 0: return 'Chờ xử lý';
      case 1: return 'Đang giao';
      case 2: return 'Hoàn thành';
      case 3: return 'Đã hủy';
      default: return 'Không rõ';
    }
  }

  /** Lịch sử đơn của user (kèm danh sách sản phẩm từng đơn). */
  getOrders(email: string): Observable<any[]> {

    return this.http.get<any[]>(this.api + '/api/orders/user/' + email).pipe(
      switchMap(orders => {

        if (!orders || orders.length === 0) {
          return of([]);
        }

        const withItems = orders.map(o =>
          this.http.get<any[]>(this.api + '/api/orderDetail/order/' + o.ordersId).pipe(
            map(details => this.mapOrder(o, details))
          )
        );

        return forkJoin(withItems);

      })
    );

  }

  /**
   * Đặt hàng: items là cart item local đã chọn
   * ({id, name, price (đã giảm), quantity, image,...}).
   */
  private pendingQuotes = new Map<string, string>();

  placeOrder(email: string, street: string, phone: string, items: any[], paymentMethod = 'COD', fromCart = false, cartRevision = 0, displayedTotal?: number, voucherCode = '', voucherDiscount = 0): Observable<any> {
    const key = JSON.stringify([email, street, phone, items.map(i => [i.id, i.quantity, i.price]), paymentMethod, fromCart, voucherCode, voucherDiscount]);
    const recipient = street.match(/\(Người nhận: (.*?)\)$/)?.[1] || 'Khách hàng';
    const address = street.replace(/\s*\(Người nhận: .*?\)$/, '');
    const expectedSubtotal = items.reduce((sum, i) => sum + Number(i.price) * i.quantity, 0);
    return this.knowledge.getFresh().pipe(switchMap(({ settings }) => {
      const expectedTotal = expectedSubtotal + StoreKnowledgeService.shipping(expectedSubtotal, settings) - voucherDiscount;
      if (displayedTotal !== undefined && displayedTotal !== expectedTotal) {
        throw new Error('Phí giao hàng đã thay đổi. Vui lòng kiểm tra lại tổng tiền trước khi đặt hàng.');
      }
      const saved = this.pendingQuotes.get(key);
      const quote = saved ? of({token: saved, total: expectedTotal}) : this.http.post<any>(this.api + '/api/chat/checkout/quote', {
        items: items.map(i => ({productId: i.id, quantity: i.quantity})), recipient, phone, street: address,
        paymentMethod, fromCart, cartRevision, voucherCode
      });
      return quote.pipe(switchMap(q => {
        if (q.total !== expectedTotal) throw new Error('Giá hoặc phí giao hàng đã thay đổi. Vui lòng tải lại giỏ và kiểm tra tổng tiền.');
        this.pendingQuotes.set(key, q.token);
        return this.http.post<any>(this.api + '/api/chat/checkout/' + encodeURIComponent(q.token) + '/confirm', {}).pipe(
          map(result => { this.pendingQuotes.delete(key); return {ordersId: result.orderId}; }),
          catchError(error => { if ([400,401,403,404,409,410].includes(error.status)) this.pendingQuotes.delete(key); return throwError(error); })
        );
      }));
    }));
  }

  /** Map order backend → shape mà orders page / profile đang dùng. */
  private mapOrder(o: any, details: any[]): any {
    return {
      id: o.ordersId,
      date: o.orderDate,
      total: o.amount,
      status: this.statusLabel(o.status),
      statusCode: o.status,
      paymentMethod: o.paymentMethod || 'COD',
      paymentStatus: o.paymentStatus || 'UNPAID',
      paymentReference: o.paymentReference || '',
      paidAt: o.paidAt || null,
      shippingAddress: {
        label: '',
        name: o.user ? o.user.name : '',
        phone: o.phone,
        street: o.address
      },
      items: (details || []).map(d => ({
        image: d.product ? d.product.image : '',
        name: d.product ? d.product.name : '',
        quantity: d.quantity,
        price: d.price
      }))
    };
  }

}

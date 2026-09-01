import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

/**
 * Đơn hàng server-side (từ 2026-06-12).
 * Flow đặt hàng: lấy Cart backend của user → cập nhật địa chỉ/SĐT →
 * push các CartDetail đã chọn → POST /api/orders/{email} (backend tạo Order,
 * dọn cart details, gửi mail xác nhận). Đơn hiện trong trang admin ngay.
 */
@Injectable({
  providedIn: 'root'
})
export class OrderService {

  private api = environment.apiUrl;

  constructor(private http: HttpClient) { }

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
  placeOrder(
    email: string,
    street: string,
    phone: string,
    items: any[],
    paymentMethod: string = 'COD'
  ): Observable<any> {

    return this.http.get<any>(this.api + '/api/cart/user/' + email).pipe(
      switchMap(cart => {

        cart.address = street;
        cart.phone = phone;
        cart.amount = items.reduce(
          (sum, i) => sum + Number(i.price) * i.quantity, 0
        );

        return this.http
          .put<any>(this.api + '/api/cart/user/' + email, cart)
          .pipe(map(() => cart));

      }),
      switchMap(cart => {

        const pushes = items.map(i =>
          this.http.post(this.api + '/api/cartDetail', {
            quantity: i.quantity,
            price: Number(i.price) * i.quantity,   // CartDetail.price = thành tiền dòng
            product: { productId: i.id },
            cart: { cartId: cart.cartId }
          })
        );

        return forkJoin(pushes).pipe(map(() => cart));

      }),
      switchMap(cart =>
        this.http.post(
          this.api + '/api/orders/' + email + '?paymentMethod=' + encodeURIComponent(paymentMethod),
          cart
        )
      )
    );

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

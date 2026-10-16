import { Component } from '@angular/core';
import { CartService } from 'src/app/services/cart.service';
import { Router } from '@angular/router';
import { NotificationService } from 'src/app/services/notification.service';
import { MotionService } from 'src/app/services/motion.service';
import { StoreKnowledgeService, StoreSettings } from 'src/app/services/store-knowledge.service';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss']
})
export class CartComponent {
cartItems: any[] = [];
shippingSettings?: StoreSettings;
shippingUnavailable = false;

constructor(
  public cartService: CartService,
  private router: Router,
  private notification: NotificationService,
  private motion: MotionService,
  private knowledge: StoreKnowledgeService
)
 {

  this.cartItems =
    this.cartService.getCartItems();
  this.knowledge.get().subscribe(data => this.shippingSettings = data.settings,
    () => this.shippingUnavailable = true);

}


increase(item: any, event: MouseEvent) {

  const max = this.maxQuantity(item);
  if (item.quantity >= max || this.cartService.syncError) return;
  item.quantity++;

  this.cartService.saveCart();

}

decrease(item: any, event: MouseEvent) {

  if (item.quantity > 1 && !this.cartService.syncError) {

    item.quantity--;

    this.cartService.saveCart();

  }

}

maxQuantity(item: any): number { return Math.max(0, Math.min(99, item.stock ?? 99)); }

// Server acknowledgements replace item objects; retain the row, cover and focus.
trackCartItem(_index: number, item: any): number { return item.id; }

getSubtotal() {

  return this.cartItems
    .filter(item => item.selected)
    .reduce(

      (total, item) =>

        total +
        item.price * item.quantity,

      0

    );

}

getTotal() {

  return this.getSubtotal() +
         this.getShippingFee();

}
getShippingFee() {

  const selectedItems =
    this.cartItems.filter(
      item => item.selected
    );

  if (selectedItems.length === 0) {

    return 0;

  }

  return this.shippingSettings ? StoreKnowledgeService.shipping(this.getSubtotal(), this.shippingSettings) : 0;

}
removeItem(item: any, event: MouseEvent) {
  const row = (event.currentTarget as HTMLElement).closest('.cart-item') as HTMLElement | null;
  this.motion.removeItem(row, () => {
    this.cartService.removeItem(item);
    this.cartItems = this.cartService.getCartItems();
  });

}

async goToCheckout(): Promise<void> {
  if (!this.shippingSettings) { this.notification.error('Chưa tải được chính sách giao hàng. Vui lòng thử lại.'); return; }
  try { await this.cartService.flush(); } catch { this.notification.error('Chưa đồng bộ được giỏ. Vui lòng tải lại giỏ trước khi đặt hàng.'); return; }
  if (this.getSubtotal() <= 0) {
    this.notification.error('Vui lòng chọn ít nhất một sản phẩm để thanh toán');
    return;
  }
  this.cartService.clearBuyNowItem();
  this.router.navigate(['/checkout']);
}
}

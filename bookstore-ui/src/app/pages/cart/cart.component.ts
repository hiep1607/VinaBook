import { Component } from '@angular/core';
import { CartService } from 'src/app/services/cart.service';
import { Router } from '@angular/router';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss']
})
export class CartComponent {
cartItems: any[] = [];

constructor(
  public cartService: CartService,
  private router: Router,
  private notification: NotificationService
)
 {

  this.cartItems =
    this.cartService.getCartItems();

}


increase(item: any) {

  item.quantity++;

  this.cartService.saveCart();

}

decrease(item: any) {

  if (item.quantity > 1) {

    item.quantity--;

    this.cartService.saveCart();

  }

}

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

  return this.getSubtotal() >= 300000 ? 0 : 20000;

}
removeItem(item: any) {

  this.cartService.removeItem(item);

  this.cartItems =
    this.cartService.getCartItems();

}

goToCheckout(): void {
  if (this.getSubtotal() <= 0) {
    this.notification.error('Vui lòng chọn ít nhất một sản phẩm để thanh toán');
    return;
  }
  this.router.navigate(['/checkout']);
}
}

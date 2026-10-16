import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { OrderService } from 'src/app/services/order.service';
import { ProductService } from 'src/app/services/product.service';
import { OrderReorderComponent } from './order-reorder.component';
@Component({ template: '<button [disabled]="busy">Tiếp tục</button><app-order-reorder [orderId]="7" (busyChange)="busy = $event"></app-order-reorder>' })
class ReorderHost { busy = false; }
describe('Mua lại đơn cũ trên website', () => {
  let fixture: ComponentFixture<OrderReorderComponent>, component: OrderReorderComponent, cart: any, auth: any, products: any;
  const book = { id: 1, name: 'Sách thử', price: 120000, quantity: 8 };
  beforeEach(async () => {
    auth = { currentUser: { email: 'buyer@test.invalid' } };
    cart = { serverRevision: 2, getCartItems: () => [], flush: jasmine.createSpy().and.returnValue(Promise.resolve()), addBooks: jasmine.createSpy() };
    products = { refresh: jasmine.createSpy().and.returnValue(of([book])) };
    await TestBed.configureTestingModule({ imports: [CommonModule, FormsModule], declarations: [OrderReorderComponent, ReorderHost],
      providers: [{ provide: AuthService, useValue: auth }, { provide: CartService, useValue: cart }, { provide: ProductService, useValue: products },
        { provide: OrderService, useValue: { reorderPreview: () => of({ orderId: 7, items: [{ productId: 1, name: book.name, quantity: 2 }] }) } }] }).compileComponents();
    fixture = TestBed.createComponent(OrderReorderComponent); component = fixture.componentInstance; component.orderId = 7;
    fixture.ngZone!.run(() => component.ngOnChanges()); await fixture.whenStable(); fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());
  it('opens inside a parent without changing its checked bindings during child initialization', async () => {
    const host = TestBed.createComponent(ReorderHost);
    expect(() => host.detectChanges()).not.toThrow();
    await host.whenStable(); host.detectChanges();
    expect(host.componentInstance.busy).toBeFalse(); host.destroy();
  });
  it('uses fresh prices and does not add anything before confirmation', () => {
    expect(component.subtotal).toBe(240000); expect(cart.addBooks).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Giá hiện tại');
  });
  it('adds all books in one cart operation and blocks duplicate clicks', async () => {
    const first = component.confirm(); void component.confirm(); await first; void component.confirm();
    expect(cart.addBooks).toHaveBeenCalledOnceWith([{ product: book, quantity: 2 }]); expect(component.done).toBeTrue();
  });
  it('blocks stale carts and out-of-stock or removed books', async () => {
    cart.serverRevision++; await component.confirm(); expect(cart.addBooks).not.toHaveBeenCalled();
    component.lines[0].product = undefined; expect(component.canAdd).toBeFalse();
  });
  it('allows selecting available books when another old book is no longer sold', async () => {
    component.lines.push({name:'Sách ngừng bán',quantity:1,selected:false});
    expect(component.canAdd).toBeTrue();expect(component.subtotal).toBe(240000);
    await component.confirm();expect(cart.addBooks).toHaveBeenCalledOnceWith([{product:book,quantity:2}]);
  });
  it('locks after an uncertain save so quantities cannot be added twice', async () => {
    let calls = 0;
    cart.flush.and.callFake(() => ++calls === 2 ? Promise.reject(new Error('Mất kết nối')) : Promise.resolve());
    await component.confirm(); await component.confirm(); expect(cart.addBooks).toHaveBeenCalledTimes(1); expect(component.uncertain).toBeTrue();
  });
  it('does not add to a different account after switching users', async () => {
    auth.currentUser = { email: 'other@test.invalid' }; component.ngDoCheck(); await component.confirm();
    expect(cart.addBooks).not.toHaveBeenCalled(); expect(component.lines).toEqual([]);
  });
});

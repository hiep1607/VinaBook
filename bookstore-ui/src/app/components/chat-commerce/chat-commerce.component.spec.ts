import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, Subject, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AddressService } from 'src/app/services/address.service';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { ChatCheckoutService } from 'src/app/services/chat-checkout.service';
import { PaymentService } from 'src/app/services/payment.service';
import { ProductService } from 'src/app/services/product.service';
import { StoreKnowledgeService } from 'src/app/services/store-knowledge.service';
import { ChatCommerceComponent } from './chat-commerce.component';

describe('Chat commerce confirmation and recovery', () => {
  let fixture: ComponentFixture<ChatCommerceComponent>;
  let component: ChatCommerceComponent;
  let cart: any, checkout: any, auth: any, payment: any;
  const book = { id: 1, name: 'Sách thử', image: '/cover.jpg', price: 100000, quantity: 5 };
  const journal = 'vinabook_chat_pending_order:' + encodeURIComponent('buyer@example.test');
  const quote = { token: 'test-token', productId: 1, name: book.name, quantity: 1, recipient: 'Khách thử',
    phone: '0900000000', street: 'Địa chỉ thử', paymentMethod: 'COD', unitPrice: 100000,
    subtotal: 100000, shipping: 20000, total: 120000, expiresAt: Date.now() + 900000,
    items: [{ productId: 1, name: book.name, quantity: 1, unitPrice: 100000 }] };

  beforeEach(async () => {
    sessionStorage.removeItem(journal);
    auth = { currentUser: { email: 'buyer@example.test', token: 'test-session', name: 'Khách thử', phone: '0900000000' } };
    cart = { items: [{ ...book, quantity: 1, stock: 5 }], serverRevision: 2, syncing: false,
      getCartItems: jasmine.createSpy().and.callFake(() => cart.items),
      flush: jasmine.createSpy().and.returnValue(Promise.resolve()), refresh: jasmine.createSpy(),
      saveCart: jasmine.createSpy().and.callFake(() => cart.serverRevision++),
      removeItem: jasmine.createSpy().and.callFake((item: any) => { cart.items = cart.items.filter((row: any) => row.id !== item.id); cart.serverRevision++; }),
      addToCart: jasmine.createSpy().and.callFake((_product: any, quantity: number) => { cart.items[0].quantity += quantity; cart.serverRevision++; }),
      clearCart: jasmine.createSpy().and.callFake(async () => { cart.items = []; cart.serverRevision++; }) };
    checkout = { quote: jasmine.createSpy().and.returnValue(of({ ...quote })),
      confirm: jasmine.createSpy().and.returnValue(of({ orderId: 91 })), cancelDraft: jasmine.createSpy().and.returnValue(of(undefined)) };
    payment = { start: jasmine.createSpy().and.returnValue(of({})) };
    await TestBed.configureTestingModule({
      declarations: [ChatCommerceComponent], imports: [CommonModule, FormsModule],
      providers: [
        { provide: CartService, useValue: cart }, { provide: AuthService, useValue: auth },
        { provide: ChatCheckoutService, useValue: checkout }, { provide: PaymentService, useValue: payment },
        { provide: AddressService, useValue: { getAddresses: () => [] } },
        { provide: ProductService, useValue: { refresh: () => of([book]) } },
        { provide: StoreKnowledgeService, useValue: { getFresh: () => of({ settings: { shippingFee: 20000, freeShippingThreshold: 300000 },
          paymentMethods: { COD: { label: 'Thanh toán khi nhận hàng', available: true }, VNPAY: { available: false } } }) } },
        { provide: Router, useValue: { navigate: jasmine.createSpy(), navigateByUrl: jasmine.createSpy() } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ChatCommerceComponent); component = fixture.componentInstance; fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); sessionStorage.removeItem(journal); });
  async function prepareQuote(fromCart = true): Promise<void> {
    await component.startCheckout(fromCart, book as any, 1);
    component.form.street = 'Địa chỉ thử'; await component.requestQuote(); fixture.detectChanges();
  }

  it('renders current quantities and shipping and does not mutate until confirmed', async () => {
    await component.showCart(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toMatch(/120[,.]000đ/);
    component.requestRemoval(component.cartItems[0]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Xóa “Sách thử” khỏi giỏ hàng?');
    expect(cart.removeItem).not.toHaveBeenCalled(); component.cancelChange();
    expect(cart.items.length).toBe(1);
  });
  it('performs a quantity update once even if confirmation is clicked twice', async () => {
    await component.showCart();
    component.runAction({ type: 'UPDATE_CART_QUANTITY', label: '', productId: 1, quantity: 2, requiresConfirmation: true }, book as any);
    await Promise.all([component.confirmChange(), component.confirmChange()]);
    expect(cart.saveCart).toHaveBeenCalledTimes(1); expect(cart.items[0].quantity).toBe(2);
    expect(component.notice).toContain('đã cập nhật giỏ hàng');
  });
  it('rejects a pending mutation if the cart changed after it was proposed', async () => {
    await component.showCart(); component.requestRemoval(component.cartItems[0]); cart.items[0].quantity = 2;
    await component.confirmChange(); expect(cart.removeItem).not.toHaveBeenCalled(); expect(component.error).toContain('Giỏ đã thay đổi');
  });
  it('does not exceed combined stock when adding another copy', async () => {
    cart.items[0].quantity = 5;
    component.runAction({ type: 'ADD_TO_CART', label: '', productId: 1, quantity: 1, requiresConfirmation: true }, book as any);
    await component.confirmChange(); expect(cart.addToCart).not.toHaveBeenCalled();
  });
  it('does not report success after a cart write failed', async () => {
    await component.showCart(); component.requestRemoval(component.cartItems[0]);
    cart.flush.and.returnValues(Promise.resolve(), Promise.reject({ error: { message: 'Giỏ thay đổi ở nơi khác' } }));
    await component.confirmChange(); expect(component.notice).toBe(''); expect(component.error).toContain('Giỏ thay đổi ở nơi khác');
    expect(component.pending).toBeUndefined();
  });
  it('clears the shared cart only after an explicit confirmation', async () => {
    await component.showCart(); component.requestClear(); expect(cart.clearCart).not.toHaveBeenCalled();
    await component.confirmChange(); expect(cart.clearCart).toHaveBeenCalledTimes(1); expect(cart.items).toEqual([]);
  });
  it('requires login before preparing a quote', async () => {
    auth.currentUser = null; await component.startCheckout(true);
    expect(component.checkout).toBeUndefined(); expect(checkout.quote).not.toHaveBeenCalled(); expect(component.error).toContain('đăng nhập');
  });
  it('quotes all cart lines with revision and waits for confirmation before ordering', async () => {
    await prepareQuote(); const input = checkout.quote.calls.mostRecent().args[0];
    expect(input.items).toEqual([{ productId: 1, quantity: 1 }]); expect(input.cartRevision).toBe(2); expect(input.fromCart).toBeTrue();
    expect(input.email).toBeUndefined(); expect(input.userId).toBeUndefined(); expect(checkout.confirm).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Xác nhận đặt hàng');
  });
  it('quotes a buy-now book without consuming or adding to the cart', async () => {
    await prepareQuote(false); const input = checkout.quote.calls.mostRecent().args[0];
    expect(input.productId).toBe(1); expect(input.fromCart).toBeFalse(); expect(input.items).toBeUndefined();
    expect(cart.addToCart).not.toHaveBeenCalled(); expect(cart.removeItem).not.toHaveBeenCalled();
  });
  it('rejects invalid delivery information before any quote request', async () => {
    await component.startCheckout(true); component.form.phone = '123'; await component.requestQuote();
    expect(checkout.quote).not.toHaveBeenCalled(); expect(component.error).toContain('10 chữ số');
  });
  it('does not confirm a quote after cart contents changed', async () => {
    await prepareQuote(); cart.items[0].quantity = 2; await component.confirmOrder();
    expect(checkout.confirm).not.toHaveBeenCalled(); expect(component.quote).toBeUndefined();
  });
  it('submits one order for a double click', async () => {
    await prepareQuote(); await Promise.all([component.confirmOrder(), component.confirmOrder()]);
    expect(checkout.confirm).toHaveBeenCalledOnceWith('test-token'); expect(component.placed?.orderId).toBe(91);
    expect(sessionStorage.getItem(journal)).toBeNull();
  });
  it('keeps the same token after an uncertain response and stores no delivery data', async () => {
    await prepareQuote(); checkout.confirm.and.returnValue(throwError({ status: 0 })); await component.confirmOrder();
    expect(component.uncertainConfirmation).toBeTrue();
    component.editQuote(); component.dismiss(); expect(component.quote?.token).toBe('test-token');
    const saved = JSON.parse(sessionStorage.getItem(journal)!);
    expect(Object.keys(saved).sort()).toEqual(['paymentMethod', 'token']);
    checkout.confirm.and.returnValue(of({ orderId: 91 })); await component.confirmOrder();
    expect(checkout.confirm.calls.allArgs()).toEqual([['test-token'], ['test-token']]); expect(component.placed?.orderId).toBe(91);
  });
  it('recovers a pending order after the widget is reopened', async () => {
    sessionStorage.setItem(journal, JSON.stringify({ token: 'same-token', paymentMethod: 'COD' })); component.reset();
    expect(component.uncertainConfirmation).toBeTrue(); await component.recoverOrder();
    expect(checkout.confirm).toHaveBeenCalledOnceWith('same-token'); expect(component.placed?.orderId).toBe(91);
  });
  it('ignores an old account quote response and clears private form state', async () => {
    await component.startCheckout(true); component.form.street = 'Địa chỉ riêng';
    const response = new Subject<any>(); checkout.quote.and.returnValue(response);
    const pending = component.requestQuote(); await Promise.resolve();
    auth.currentUser = { email: 'other@example.test', token: 'other' }; component.reset();
    response.next(quote); response.complete(); await pending;
    expect(component.quote).toBeUndefined(); expect(component.form.street).toBe(''); expect(component.visible).toBeFalse();
  });
  it('a payment failure retries payment on the created order instead of placing again', async () => {
    checkout.quote.and.returnValue(of({ ...quote, paymentMethod: 'BANK_QR' }));
    payment.start.and.returnValue(throwError({ status: 0 })); await prepareQuote(); await component.confirmOrder();
    expect(component.placed?.orderId).toBe(91); expect(component.error).toContain('Đơn đã được tạo');
    await component.confirmOrder(); await component.startPayment();
    expect(checkout.confirm).toHaveBeenCalledTimes(1); expect(payment.start.calls.allArgs()).toEqual([[91], [91]]);
  });
});

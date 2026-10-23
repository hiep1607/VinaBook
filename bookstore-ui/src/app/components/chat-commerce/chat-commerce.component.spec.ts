import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
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
import { DeliveryAddressComponent } from '../delivery-address/delivery-address.component';
import { DeliveryLocationService } from 'src/app/services/delivery-location.service';

describe('Chat commerce confirmation and recovery', () => {
  let fixture: ComponentFixture<ChatCommerceComponent>;
  let component: ChatCommerceComponent;
  let cart: any, checkout: any, auth: any, payment: any, addresses: any;
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
    checkout = { quote: jasmine.createSpy().and.callFake((input: any) => of(serverQuote(input))),
      confirm: jasmine.createSpy().and.returnValue(of({ orderId: 91 })), cancelDraft: jasmine.createSpy().and.returnValue(of(undefined)) };
    payment = { start: jasmine.createSpy().and.returnValue(of({})) };
    addresses = { refresh: jasmine.createSpy().and.returnValue(Promise.resolve()),
      addAddress: jasmine.createSpy().and.returnValue(Promise.resolve()),
      getAddresses: jasmine.createSpy().and.returnValue([]) };
    await TestBed.configureTestingModule({
      declarations: [ChatCommerceComponent, DeliveryAddressComponent], imports: [CommonModule, FormsModule], schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: DeliveryLocationService, useValue: { provinces: () => Promise.resolve([{code: 1, name: 'Hà Nội'}]) } },
        { provide: CartService, useValue: cart }, { provide: AuthService, useValue: auth },
        { provide: ChatCheckoutService, useValue: checkout }, { provide: PaymentService, useValue: payment },
        { provide: AddressService, useValue: addresses },
        { provide: ProductService, useValue: { refresh: () => of([book]) } },
        { provide: StoreKnowledgeService, useValue: { getFresh: () => of({ settings: { shippingFee: 20000, freeShippingThreshold: 300000 },
          paymentMethods: { COD: { label: 'Thanh toán khi nhận hàng', available: true }, BANK_QR: { label: 'Chuyển khoản', available: true }, VNPAY: { available: false } } }) } },
        { provide: Router, useValue: { navigate: jasmine.createSpy(), navigateByUrl: jasmine.createSpy() } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ChatCommerceComponent); component = fixture.componentInstance; fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); sessionStorage.removeItem(journal); });
  function serverQuote(input: any, token = 'test-token'): any {
    const items = input.items || [{ productId: input.productId, quantity: input.quantity }];
    const lines = items.map((item: any) => ({ ...item, name: book.name, unitPrice: book.price }));
    const subtotal = lines.reduce((sum: number, item: any) => sum + item.quantity * item.unitPrice, 0);
    const shipping = subtotal < 300000 ? 20000 : 0;
    const discount = input.voucherCode ? Math.round(subtotal * .1) : 0;
    return { ...quote, ...input, token, items: lines, subtotal, shipping, discount,
      total: subtotal + shipping - discount, expiresAt: Date.now() + 900000 };
  }
  async function prepareQuote(fromCart = true): Promise<void> {
    await component.startCheckout(fromCart, book as any, 1);
    component.form.street = 'Địa chỉ thử'; await component.requestQuote(); fixture.detectChanges();
  }

  function savedAddresses(): void {
    addresses.getAddresses.and.returnValue([
      { id: 1, label: 'Công ty', name: 'Người nhận công ty', phone: '0900000001', street: 'Địa chỉ công ty' },
      { id: 2, label: 'Nhà riêng', name: 'Người nhận mặc định', phone: '0900000002', street: 'Địa chỉ mặc định', isDefault: true }
    ]);
  }
  it('selects and displays the default address first in one address section', fakeAsync(() => {
    savedAddresses(); void component.startCheckout(false, book as any, 1); flushMicrotasks();
    fixture.detectChanges(); flushMicrotasks(); tick(1000); flushMicrotasks(); fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select[name="savedAddress"]');
    expect(select.value).toBe('2'); expect(select.options[0].textContent).toContain('Mặc định');
    expect(fixture.nativeElement.querySelectorAll('fieldset.delivery-address').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.address-details').textContent).toContain('Địa chỉ mặc định');
    expect(fixture.nativeElement.querySelector('textarea[name="street"]')).toBeNull();
    expect(addresses.getAddresses.calls.mostRecent().returnValue[0].id).toBe(1);
    component.reset(); flushMicrotasks();
  }));
  it('updates delivery details and quote input when another saved address is selected', fakeAsync(() => {
    savedAddresses(); void component.startCheckout(false, book as any, 1); flushMicrotasks();
    fixture.detectChanges(); flushMicrotasks(); tick(1000); flushMicrotasks(); fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select[name="savedAddress"]');
    select.value = '1'; select.dispatchEvent(new Event('change')); fixture.detectChanges();
    expect(component.form.street).toBe('Địa chỉ công ty');
    expect(fixture.nativeElement.querySelector('.address-details').textContent).toContain('Người nhận công ty');
    void component.requestQuote(); flushMicrotasks();
    expect(checkout.quote).toHaveBeenCalledWith(jasmine.objectContaining({ recipient: 'Người nhận công ty', phone: '0900000001', street: 'Địa chỉ công ty' }));
    expect(checkout.confirm).not.toHaveBeenCalled();
    component.reset(); flushMicrotasks();
  }));
  it('allows editing for this order or entering a different address without changing saved addresses', async () => {
    savedAddresses(); await component.startCheckout(false, book as any, 1); fixture.detectChanges();
    fixture.nativeElement.querySelector('.address-details button').click(); fixture.detectChanges();
    expect(component.selectedAddressId).toBe('');
    expect(fixture.nativeElement.querySelector('app-delivery-address textarea')).not.toBeNull();
    expect(component.form.street).toBe('Địa chỉ mặc định');
    component.form.street = 'Địa chỉ chỉ dùng cho đơn này';
    expect(component.addresses[0].street).toBe('Địa chỉ mặc định');
    component.useAddress('1'); component.useAddress(''); fixture.detectChanges();
    expect(component.editingAddress).toBeTrue(); expect(component.form.street).toBe('');
    component.reset(); expect(component.selectedAddressId).toBe(''); expect(component.addresses).toEqual([]);
  });

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
  it('retains the selected purchase while requiring login before preparing a quote', async () => {
    auth.currentUser = null; await component.startCheckout(true);
    expect(component.checkout?.fromCart).toBeTrue(); expect(checkout.quote).not.toHaveBeenCalled();
    fixture.detectChanges(); expect(component.canConfirmOrder).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Đăng nhập');
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
    expect(addresses.addAddress).not.toHaveBeenCalled();
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
    expect(addresses.addAddress).not.toHaveBeenCalled();
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
  it('ignores an old account quote response and clears private form state', fakeAsync(() => {
    void component.startCheckout(true); flushMicrotasks(); component.form.street = 'Địa chỉ riêng';
    const response = new Subject<any>(); checkout.quote.and.returnValue(response);
    void component.requestQuote(); flushMicrotasks();
    auth.currentUser = { email: 'other@example.test', token: 'other' }; component.reset();
    response.next(quote); response.complete(); flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(component.quote).toBeUndefined(); expect(component.form.street).toBe(''); expect(component.visible).toBeFalse();
  }));
  it('a payment failure retries payment on the created order instead of placing again', async () => {
    payment.start.and.returnValue(throwError({ status: 0 }));
    await component.startCheckout(true); component.form.street = 'Địa chỉ thử'; component.form.paymentMethod = 'BANK_QR';
    component.deliveryChanged(); await component.requestQuote(); await component.confirmOrder();
    expect(component.placed?.orderId).toBe(91); expect(component.error).toContain('Đơn đã được tạo');
    await component.confirmOrder(); await component.startPayment();
    expect(checkout.confirm).toHaveBeenCalledTimes(1); expect(payment.start.calls.allArgs()).toEqual([[91], [91]]);
  });

  it('automatically prepares the default address and keeps one editable checkout form', fakeAsync(() => {
    savedAddresses(); void component.startCheckout(false, book as any, 1); flushMicrotasks();
    tick(1000); flushMicrotasks(); fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    expect(checkout.quote).toHaveBeenCalledOnceWith(jasmine.objectContaining({
      productId: 1, quantity: 1, recipient: 'Người nhận mặc định', street: 'Địa chỉ mặc định'
    }));
    expect(component.canConfirmOrder).toBeTrue(); expect(component.quoteUpdating).toBeFalse();
    expect(fixture.nativeElement.querySelectorAll('form').length).toBe(1);
    expect(fixture.nativeElement.querySelector('select[name="savedAddress"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Xem báo giá');
    expect(checkout.confirm).not.toHaveBeenCalled(); component.reset(); flushMicrotasks();
  }));

  it('updates buy-now totals immediately while automatically refreshing the server quote', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ thử'; void component.requestQuote(); flushMicrotasks();
    expect(component.checkoutTotal).toBe(120000); expect(component.canConfirmOrder).toBeTrue();
    checkout.quote.calls.reset();
    const updating = component.changeCheckoutQuantity(1, 3);
    expect(component.checkoutSubtotal).toBe(300000); expect(component.checkoutShipping).toBe(0);
    expect(component.checkoutTotal).toBe(300000); expect(component.totalEstimated).toBeTrue();
    expect(component.canConfirmOrder).toBeFalse(); expect(checkout.quote).not.toHaveBeenCalled();
    flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(checkout.quote).toHaveBeenCalledOnceWith(jasmine.objectContaining({ productId: 1, quantity: 3 }));
    expect(component.checkoutTotal).toBe(300000); expect(component.totalEstimated).toBeFalse();
    expect(component.canConfirmOrder).toBeTrue(); expect(cart.saveCart).not.toHaveBeenCalled();
    void updating; component.reset(); flushMicrotasks();
  }));

  it('keeps the quantity input and focus while editing and receiving an updated quote', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks(); component.form.street = 'Địa chỉ thử';
    void component.requestQuote(); flushMicrotasks(); fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('.quantity-stepper input');
    input.focus(); expect(document.activeElement).toBe(input);
    const response = new Subject<any>(); checkout.quote.and.returnValue(response);
    input.value = '2'; input.dispatchEvent(new Event('input')); fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    expect(component.checkout?.quantity).toBe(2);
    expect(fixture.nativeElement.querySelector('.quantity-stepper input')).toBe(input);
    expect(document.activeElement).toBe(input);
    tick(1000); flushMicrotasks();
    response.next(serverQuote(checkout.quote.calls.mostRecent().args[0])); response.complete(); flushMicrotasks(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.quantity-stepper input')).toBe(input);
    expect(document.activeElement).toBe(input); expect(input.value).toBe('2');
    component.reset(); flushMicrotasks();
  }));

  it('writes a cart quantity before quoting its new revision and updates money before the write settles', fakeAsync(() => {
    void component.startCheckout(true); flushMicrotasks();
    component.form.street = 'Địa chỉ thử'; void component.requestQuote(); flushMicrotasks();
    let finishWrite!: () => void;
    cart.flush.and.returnValue(new Promise<void>(resolve => { finishWrite = resolve; }));
    checkout.quote.calls.reset();
    void component.changeCheckoutQuantity(1, 2);
    expect(component.checkoutSubtotal).toBe(200000); expect(component.checkoutTotal).toBe(220000);
    expect(component.canConfirmOrder).toBeFalse();
    tick(1000); flushMicrotasks(); expect(checkout.quote).not.toHaveBeenCalled();
    finishWrite(); flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(cart.items[0].quantity).toBe(2); expect(cart.saveCart).toHaveBeenCalledTimes(1);
    expect(checkout.quote).toHaveBeenCalledOnceWith(jasmine.objectContaining({
      items: [{ productId: 1, quantity: 2 }], cartRevision: cart.serverRevision
    }));
    component.reset(); flushMicrotasks();
  }));

  it('serializes quote requests and coalesces edits without accepting the older response', fakeAsync(() => {
    const first = new Subject<any>(), latest = new Subject<any>();
    checkout.quote.and.returnValues(first, latest);
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ đầu'; component.deliveryChanged(); void component.requestQuote(); flushMicrotasks();
    expect(checkout.quote).toHaveBeenCalledTimes(1); expect(component.quoteUpdating).toBeTrue();
    void component.changeCheckoutQuantity(1, 2); flushMicrotasks();
    void component.changeCheckoutQuantity(1, 3); flushMicrotasks();
    component.form.street = 'Địa chỉ cuối'; component.form.paymentMethod = 'BANK_QR';
    component.deliveryChanged(); tick(1000); flushMicrotasks();
    expect(checkout.quote).toHaveBeenCalledTimes(1); expect(component.checkoutTotal).toBe(300000);
    const olderInput = checkout.quote.calls.first().args[0];
    first.next(serverQuote(olderInput, 'older-token')); first.complete(); flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(component.quote?.token).not.toBe('older-token'); expect(component.canConfirmOrder).toBeFalse();
    expect(checkout.quote).toHaveBeenCalledTimes(2);
    const latestInput = checkout.quote.calls.mostRecent().args[0];
    expect(latestInput).toEqual(jasmine.objectContaining({ quantity: 3, street: 'Địa chỉ cuối', paymentMethod: 'BANK_QR' }));
    latest.next(serverQuote(latestInput, 'latest-token')); latest.complete(); flushMicrotasks();
    expect(component.quote?.token).toBe('latest-token'); expect(component.canConfirmOrder).toBeTrue();
    expect(checkout.confirm).not.toHaveBeenCalled(); component.reset(); flushMicrotasks();
  }));

  it('keeps and revalidates an applied voucher across quantity, address and payment changes', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ thử'; component.voucherCode = 'READ10'; component.deliveryChanged();
    void component.requestQuote(); flushMicrotasks(); expect(component.checkoutTotal).toBe(110000);
    void component.changeCheckoutQuantity(1, 3); flushMicrotasks();
    component.form.street = 'Địa chỉ mới'; component.form.paymentMethod = 'BANK_QR'; component.deliveryChanged();
    expect(component.voucherCode).toBe('READ10'); expect(component.canConfirmOrder).toBeFalse();
    tick(1000); flushMicrotasks();
    expect(checkout.quote.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({
      quantity: 3, street: 'Địa chỉ mới', paymentMethod: 'BANK_QR', voucherCode: 'READ10'
    }));
    expect(component.checkoutTotal).toBe(270000); expect(component.voucherCode).toBe('READ10');
    expect(component.canConfirmOrder).toBeTrue(); component.reset(); flushMicrotasks();
  }));

  it('never confirms an old quote after an edit fails validation or the new quote fails', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ thử'; void component.requestQuote(); flushMicrotasks();
    expect(component.canConfirmOrder).toBeTrue();
    component.form.phone = '123'; component.deliveryChanged(); tick(1000); flushMicrotasks();
    expect(component.inputValid).toBeFalse(); expect(component.canConfirmOrder).toBeFalse();
    const count = checkout.quote.calls.count(); void component.confirmOrder(); flushMicrotasks();
    expect(checkout.confirm).not.toHaveBeenCalled(); expect(checkout.quote.calls.count()).toBe(count);
    component.form.phone = '0900000000'; checkout.quote.and.returnValue(throwError({ status: 0 }));
    component.deliveryChanged(); tick(1000); flushMicrotasks();
    expect(component.canConfirmOrder).toBeFalse(); expect(component.quoteUpdating).toBeFalse();
    expect(component.error).not.toBe('');
    checkout.quote.and.callFake((input: any) => of(serverQuote(input, 'retry-token')));
    void component.requestQuote(); flushMicrotasks(); expect(component.canConfirmOrder).toBeTrue();
    expect(component.quote?.token).toBe('retry-token'); component.reset(); flushMicrotasks();
  }));

  it('rejects invalid checkout quantities without writing the cart or submitting a new quote', fakeAsync(() => {
    void component.startCheckout(true); flushMicrotasks(); component.form.street = 'Địa chỉ thử';
    void component.requestQuote(); flushMicrotasks(); checkout.quote.calls.reset();
    for (const quantity of [0, -1, 1.5, 6, 100, 'no-number']) {
      void component.changeCheckoutQuantity(1, quantity); flushMicrotasks();
    }
    tick(1000); flushMicrotasks();
    expect(cart.items[0].quantity).toBe(1); expect(cart.saveCart).not.toHaveBeenCalled();
    expect(checkout.quote).not.toHaveBeenCalled(); expect(component.quantityError).not.toBe('');
    component.reset(); flushMicrotasks();
  }));

  it('does not let a pending quote overwrite a changed external cart', fakeAsync(() => {
    const response = new Subject<any>(); checkout.quote.and.returnValue(response);
    void component.startCheckout(true); flushMicrotasks(); component.form.street = 'Địa chỉ thử';
    void component.requestQuote(); flushMicrotasks();
    const input = checkout.quote.calls.mostRecent().args[0];
    cart.items[0].quantity = 2; cart.serverRevision++;
    response.next(serverQuote(input)); response.complete(); flushMicrotasks();
    expect(component.canConfirmOrder).toBeFalse();
    void component.confirmOrder(); flushMicrotasks(); expect(checkout.confirm).not.toHaveBeenCalled();
    component.reset(); flushMicrotasks();
  }));

  it('requires a fresh quote after expiry without creating an order automatically', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks(); component.form.street = 'Địa chỉ thử';
    void component.requestQuote(); flushMicrotasks(); expect(component.canConfirmOrder).toBeTrue();
    component.quote!.expiresAt = Date.now() - 1;
    expect(component.canConfirmOrder).toBeFalse(); void component.confirmOrder(); flushMicrotasks();
    expect(checkout.confirm).not.toHaveBeenCalled();
    void component.requestQuote(); flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(component.canConfirmOrder).toBeTrue(); expect(checkout.confirm).not.toHaveBeenCalled();
    component.reset(); flushMicrotasks();
  }));

  it('blocks automatic quote replacement and quantity edits while confirmation is uncertain', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ thử'; void component.requestQuote(); flushMicrotasks();
    checkout.confirm.and.returnValue(throwError({ status: 0 })); void component.confirmOrder(); flushMicrotasks();
    expect(component.uncertainConfirmation).toBeTrue(); const count = checkout.quote.calls.count();
    void component.changeCheckoutQuantity(1, 3); component.deliveryChanged(); void component.requestQuote();
    flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(component.checkout?.quantity).toBe(1); expect(checkout.quote.calls.count()).toBe(count);
    expect(component.quote?.token).toBe('test-token');
    checkout.confirm.and.returnValue(of({ orderId: 91 })); void component.confirmOrder(); flushMicrotasks();
    expect(checkout.confirm.calls.allArgs()).toEqual([['test-token'], ['test-token']]);
    expect(component.placed?.orderId).toBe(91); component.reset(); flushMicrotasks();
  }));

  it('adds directly once and can undo the exact previous cart quantity', fakeAsync(() => {
    void component.showCart(); flushMicrotasks();
    component.runAction({ type: 'ADD_TO_CART', label: 'Thêm giỏ', productId: 1, quantity: 1,
      requiresConfirmation: true }, book as any, component.fingerprint(), true);
    flushMicrotasks();
    expect(component.pending).toBeUndefined(); expect(cart.addToCart).toHaveBeenCalledTimes(1);
    expect(cart.items[0].quantity).toBe(2); expect(component.canUndo).toBeTrue();
    void component.undoLastChange(); flushMicrotasks();
    expect(cart.items[0].quantity).toBe(1); expect(component.canUndo).toBeFalse();
    component.reset(); flushMicrotasks();
  }));

  it('refuses undo when the cart has changed since a direct add', fakeAsync(() => {
    void component.showCart(); flushMicrotasks();
    component.runAction({ type: 'ADD_TO_CART', label: 'Thêm giỏ', productId: 1, quantity: 1,
      requiresConfirmation: true }, book as any, component.fingerprint(), true);
    flushMicrotasks(); cart.items[0].quantity = 3; cart.serverRevision++;
    const writes = cart.saveCart.calls.count(); void component.undoLastChange(); flushMicrotasks();
    expect(cart.items[0].quantity).toBe(3); expect(cart.saveCart.calls.count()).toBe(writes);
    expect(component.canUndo).toBeFalse(); component.reset(); flushMicrotasks();
  }));

  it('updates a cart quantity directly without asking for a second confirmation', fakeAsync(() => {
    void component.showCart(); flushMicrotasks();
    const input = document.createElement('input'); input.value = '2';
    component.requestQuantity(component.cartItems[0], { target: input } as any);
    flushMicrotasks(); expect(component.pending).toBeUndefined();
    expect(cart.items[0].quantity).toBe(2); expect(component.subtotal + component.shipping).toBe(220000);
    expect(component.canUndo).toBeTrue(); component.reset(); flushMicrotasks();
  }));

  it('applies explicit checkout chat edits to the same form and refreshes totals without placing an order', fakeAsync(() => {
    savedAddresses(); void component.startCheckout(false, book as any, 1); flushMicrotasks(); tick(1000); flushMicrotasks();
    void component.handleCheckoutMessage('đổi thành 3 cuốn'); flushMicrotasks();
    expect(component.checkout?.quantity).toBe(3); expect(component.checkoutTotal).toBe(300000);
    void component.handleCheckoutMessage('giao về địa chỉ công ty'); flushMicrotasks();
    expect(component.form.street).toBe('Địa chỉ công ty');
    void component.handleCheckoutMessage('thanh toán chuyển khoản'); flushMicrotasks();
    expect(component.form.paymentMethod).toBe('BANK_QR');
    void component.handleCheckoutMessage('mã: READ10'); flushMicrotasks(); tick(1000); flushMicrotasks();
    expect(checkout.quote.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({
      quantity: 3, street: 'Địa chỉ công ty', paymentMethod: 'BANK_QR', voucherCode: 'READ10'
    }));
    expect(component.checkoutTotal).toBe(270000); expect(checkout.confirm).not.toHaveBeenCalled();
    component.reset(); flushMicrotasks();
  }));

  it('does not treat negated or informational checkout messages as product edits', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks(); component.form.street = 'Địa chỉ thử';
    void component.requestQuote(); flushMicrotasks(); const count = checkout.quote.calls.count();
    for (const message of ['không đổi thành 2 cuốn', 'mua 3 cuốn thì có miễn phí giao hàng không?', 'không dùng mã READ10', 'không thanh toán chuyển khoản']) {
      void component.handleCheckoutMessage(message); flushMicrotasks();
    }
    tick(1000); flushMicrotasks();
    expect(component.checkout?.quantity).toBe(1); expect(component.voucherCode).toBe('');
    expect(component.form.paymentMethod).toBe('COD'); expect(checkout.quote.calls.count()).toBe(count);
    expect(checkout.confirm).not.toHaveBeenCalled(); component.reset(); flushMicrotasks();
  }));

  it('accepts the title Không Gia Đình while respecting negated switches and another book name', fakeAsync(() => {
    const second = { ...book, id: 2, name: 'Không Gia Đình', price: 80000 };
    spyOn(TestBed.inject(ProductService), 'refresh').and.returnValue(of([book, second]));
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    void component.handleCheckoutMessage('đổi sang Không Gia Đình'); flushMicrotasks();
    expect(component.checkout?.product?.id).toBe(2); expect(component.checkout?.quantity).toBe(1);
    void component.handleCheckoutMessage('không đổi sang Sách thử'); flushMicrotasks();
    expect(component.checkout?.product?.id).toBe(2);
    void component.handleCheckoutMessage('đổi Sách thử thành 2 cuốn'); flushMicrotasks();
    expect(component.checkout?.product?.id).toBe(2); expect(component.checkout?.quantity).toBe(1);
    expect(checkout.confirm).not.toHaveBeenCalled(); component.reset(); flushMicrotasks();
  }));

  it('saves an opted-in delivery address from the accepted quote only after the order is confirmed', fakeAsync(() => {
    void component.startCheckout(false, book as any, 1); flushMicrotasks();
    component.form.street = 'Địa chỉ nhận đã xác nhận'; component.saveDeliveryAddress = true;
    void component.requestQuote(); flushMicrotasks();
    expect(addresses.addAddress).not.toHaveBeenCalled();
    const response = new Subject<any>(); checkout.confirm.and.returnValue(response);
    void component.confirmOrder(); flushMicrotasks();
    expect(checkout.confirm).toHaveBeenCalledOnceWith('test-token');
    expect(addresses.addAddress).not.toHaveBeenCalled();
    component.form.street = 'Nội dung thay đổi sau khi gửi xác nhận';
    response.next({ orderId: 91 }); response.complete(); flushMicrotasks();
    expect(component.placed?.orderId).toBe(91);
    expect(addresses.addAddress).toHaveBeenCalledOnceWith('buyer@example.test', {
      name: 'Khách thử', phone: '0900000000', street: 'Địa chỉ nhận đã xác nhận', label: 'Địa chỉ'
    });
    component.reset(); flushMicrotasks();
  }));

  it('keeps the created order and never confirms it twice when optional address saving fails', async () => {
    addresses.addAddress.and.callFake(() => Promise.reject(new Error('Không lưu được địa chỉ')));
    await prepareQuote(false); component.saveDeliveryAddress = true;
    await component.confirmOrder();
    expect(component.placed?.orderId).toBe(91); expect(component.notice).toContain('#91');
    expect(component.error).not.toBe(''); expect(sessionStorage.getItem(journal)).toBeNull();
    await component.confirmOrder();
    expect(checkout.confirm).toHaveBeenCalledOnceWith('test-token');
    expect(addresses.addAddress).toHaveBeenCalledTimes(1);
  });
});

import { fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { of } from 'rxjs';
import { CheckoutComponent } from '../pages/checkout/checkout.component';
import { LoginComponent } from '../pages/login/login.component';
import { CartService } from './cart.service';
import { ChatCheckoutService } from './chat-checkout.service';
import { ProductCardComponent } from '../components/product-card/product-card.component';

describe('Checkout selection and immediate totals', () => {
  let component: CheckoutComponent, cart: any, addresses: any, products: any, router: any;
  const book = { id: 57, name: 'Sách thử', price: 70520, quantity: 8, image: '' };
  beforeEach(() => {
    cart = { buyNowItem: null, serverRevision: 1, getCartItems: () => [{ ...book, quantity: 2, selected: true }],
      getBuyNowSelection: () => undefined, flush: () => Promise.resolve(), saveCart: jasmine.createSpy(), clearBuyNowItem: jasmine.createSpy(),
      setBuyNowItem: jasmine.createSpy().and.callFake((p: any, quantity: number) => cart.buyNowItem = { ...p, quantity, stock: p.quantity }) };
    addresses = { refresh: () => Promise.resolve(), getAddresses: () => [], getDefault: () => undefined };
    products = { refresh: () => of([book]) }; router = { navigate: jasmine.createSpy() };
    component = new CheckoutComponent(cart, { placeOrder: jasmine.createSpy() } as any,
      { currentUser: { email: 'qa@test.invalid', name: 'Người thử', phone: '0900000101' } } as any, addresses,
      { getMethods: () => of({ COD: { available: true } }) } as any, router, { error: jasmine.createSpy() } as any, {} as any,
      { get: () => of({ settings: { shippingFee: 20000, freeShippingThreshold: 300000 } }) } as any, products);
  });
  it('restores buy-now from IDs using fresh catalog instead of cart contents', async () => {
    cart.getBuyNowSelection = () => ({ productId: 57, quantity: 3 }); await component.ngOnInit();
    expect(component.buyNowMode).toBeTrue(); expect(component.cartItems[0].quantity).toBe(3);
    expect(component.cartItems[0].price).toBe(70520); expect(router.navigate).not.toHaveBeenCalled();
  });
  it('fills profile fields and updates money and shipping immediately', async () => {
    await component.ngOnInit(); expect(component.shippingForm.name).toBe('Người thử'); expect(component.shippingForm.phone).toBe('0900000101');
    component.changeQuantity(component.cartItems[0], 5); expect(component.getSubtotal()).toBe(352600); expect(component.getShipping()).toBe(0); expect(component.getTotal()).toBe(352600);
    component.changeQuantity(component.cartItems[0], 2); expect(component.getTotal()).toBe(161040); expect(cart.saveCart).toHaveBeenCalledTimes(2);
  });
  it('selects the default address and collapses after choosing another', async () => {
    const first = { id: 1, name: 'A' }, second = { id: 2, name: 'B', isDefault: true };
    addresses.getAddresses = () => [first, second]; addresses.getDefault = () => second;
    await component.ngOnInit(); expect(component.selectedAddress).toBe(second); expect(component.showNewForm).toBeFalse();
    component.choosingAddress = true; component.selectAddress(first); expect(component.choosingAddress).toBeFalse(); expect(component.selectedAddress).toBe(first);
  });
  it('does not replace an unavailable saved selection with other cart items', async () => {
    cart.getBuyNowSelection = () => ({ productId: 57, quantity: 9 }); await component.ngOnInit();
    expect(cart.clearBuyNowItem).toHaveBeenCalled(); expect(router.navigate).toHaveBeenCalledWith(['/product', 57]); expect(component.cartItems).toEqual([]);
  });
  it('blocks submission for stock shortage or pending voucher', async () => {
    await component.ngOnInit(); component.cartItems[0].quantity = 9; await component.placeOrder();
    expect(component.formError).toContain('tồn kho'); component.cartItems[0].quantity = 1; component.voucherBusy = true;
    await component.placeOrder(); expect((component as any).orderService.placeOrder).not.toHaveBeenCalled();
  });
  it('caps quantity to stock and keeps draft across teardown', async () => {
    cart.getBuyNowSelection = () => ({ productId: 57, quantity: 1 }); await component.ngOnInit();
    component.changeQuantity(component.cartItems[0], 100); expect(component.cartItems[0].quantity).toBe(8);
    component.ngOnDestroy(); expect(cart.clearBuyNowItem).not.toHaveBeenCalled();
  });
});

describe('Resume website purchase after login', () => {
  let component: LoginComponent, router: any, cart: any, intent: any;
  beforeEach(() => {
    router = { navigate: jasmine.createSpy(), navigateByUrl: jasmine.createSpy() };
    cart = { flush: () => Promise.resolve(), setBuyNowItem: jasmine.createSpy(), addToCart: jasmine.createSpy() };
    intent = { productId: 57, quantity: 2, action: 'BUY_NOW', surface: 'web', fromCart: false };
    component = new LoginComponent({ login: () => of(true), currentUser: { email: 'qa@test.invalid' } } as any, router,
      { success: () => {}, error: jasmine.createSpy() } as any, { snapshot: { queryParamMap: { get: () => '/checkout' } } } as any,
      cart, { refresh: () => of([{ id: 57, name: 'Sách', quantity: 5, price: 70520 }]) } as any,
      { consumePurchaseIntent: () => intent } as any);
  });
  it('keeps book and quantity and blocks duplicate login submissions', fakeAsync(() => {
    component.login(); component.login(); flushMicrotasks();
    expect(cart.setBuyNowItem).toHaveBeenCalledOnceWith(jasmine.objectContaining({id:57}), 2);
    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/checkout'); expect(component.loading).toBeFalse();
  }));
  it('resumes add-to-cart after server synchronization', fakeAsync(() => {
    intent.action = 'ADD_TO_CART'; component.login(); flushMicrotasks();
    expect(cart.addToCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({id:57}), 2);
    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/cart');
  }));
  it('returns unavailable books for correction without buying another book', fakeAsync(() => {
    intent.quantity = 8; component.login(); flushMicrotasks();
    expect(cart.setBuyNowItem).not.toHaveBeenCalled(); expect(router.navigate).toHaveBeenCalledWith(['/product',57], { queryParams: { quantity:8 } });
  }));
});

describe('Scoped commerce selections', () => {
  let cart: CartService, auth: any;
  beforeEach(() => { auth = { currentUser: { email: 'audit-selection@test.invalid' } }; cart = new CartService(auth); });
  afterEach(() => { cart.clearBuyNowItem(); cart.ngOnDestroy(); localStorage.removeItem('cart_audit-selection@test.invalid'); });
  it('restores only IDs and quantities across service recreation', () => {
    cart.setBuyNowItem({id:57, name:'Sách', quantity:8, price:70520}, 3);
    const restored = new CartService(auth); expect(restored.getBuyNowSelection()).toEqual({productId:57, quantity:3}); restored.ngOnDestroy();
    const stored = sessionStorage.getItem('vinabook_web_buy_now_v1')!; expect(stored).not.toContain('price'); expect(stored).not.toContain('Sách');
  });
  it('rejects other-account and expired selections', () => {
    cart.setBuyNowItem({id:57, quantity:8}, 2); auth.currentUser.email = 'other@test.invalid'; expect(cart.getBuyNowSelection()).toBeUndefined();
    auth.currentUser.email = 'audit-selection@test.invalid'; cart.setBuyNowItem({id:57, quantity:8}, 2);
    const key='vinabook_web_buy_now_v1', stored=JSON.parse(sessionStorage.getItem(key)!); stored.expiresAt=1;sessionStorage.setItem(key,JSON.stringify(stored));
    expect(cart.getBuyNowSelection()).toBeUndefined();
  });
  it('rejects overflow without silently reducing requested cart quantity', () => {
    const book={id:57,name:'Sách',quantity:4};cart.addToCart(book,3);
    expect(()=>cart.addToCart(book,2)).toThrow(); expect(cart.getCartItems()[0].quantity).toBe(3);
  });
  it('keeps web intent for login instead of letting the chat widget consume it', () => {
    const service=new ChatCheckoutService({} as any);
    service.rememberPurchaseIntent({surface:'web',action:'BUY_NOW',fromCart:false,productId:57,quantity:2});
    expect(service.consumePurchaseIntent()).toBeUndefined(); expect(service.consumePurchaseIntent('web')?.quantity).toBe(2);expect(service.consumePurchaseIntent('web')).toBeUndefined();
  });
});

describe('Cart save receipts on a book card', () => {
  it('waits for server acknowledgment before reporting success', async () => {
    let acknowledge!: () => void; const saved = new Promise<void>(resolve=>acknowledge=resolve); let calls=0;
    const notification={success:jasmine.createSpy(),error:jasmine.createSpy()};
    const component=new ProductCardComponent({} as any,{} as any,{} as any,
      {isLoggedIn:()=>true,currentUser:{email:'qa@test.invalid'}} as any,
      {flush:()=>++calls===1?Promise.resolve():saved,addToCart:()=>{}} as any,notification as any,{addToCart:()=>{}} as any);
    component.product={id:57,quantity:8};const operation=component.addToCart({stopPropagation:()=>{},currentTarget:{}} as any);
    await Promise.resolve();expect(notification.success).not.toHaveBeenCalled();expect(component.adding).toBeTrue();
    acknowledge();await operation;expect(notification.success).toHaveBeenCalledOnceWith('Đã thêm vào giỏ hàng');expect(component.adding).toBeFalse();
  });
  it('does not report success after a rejected save', async () => {
    const notification={success:jasmine.createSpy(),error:jasmine.createSpy()};let calls=0;
    const component=new ProductCardComponent({} as any,{} as any,{} as any,
      {isLoggedIn:()=>true,currentUser:{email:'qa@test.invalid'}} as any,
      {flush:()=>++calls===1?Promise.resolve():Promise.reject(new Error('Mất kết nối')),addToCart:()=>{}} as any,notification as any,{} as any);
    component.product={id:57,quantity:8};await component.addToCart({stopPropagation:()=>{},currentTarget:{}} as any);
    expect(notification.success).not.toHaveBeenCalled();expect(notification.error).toHaveBeenCalledOnceWith('Mất kết nối');
  });
});

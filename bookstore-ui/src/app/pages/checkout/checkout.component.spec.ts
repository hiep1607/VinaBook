import { of, Subject, throwError } from 'rxjs';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let component: CheckoutComponent;
  let cart: any, router: any, order: any, knowledge: any;
  const catalog = [{id: 7, name: 'Sách thử', quantity: 10, price: 100000}, {id: 9, name: 'Mua ngay', quantity: 10, price: 50000}];
  beforeEach(() => {
    cart = {buyNowItem: null, getCartItems: jasmine.createSpy().and.returnValue([{id: 7, quantity: 1, selected: true}]),
      getBuyNowSelection: jasmine.createSpy().and.returnValue(undefined), clearBuyNowItem: jasmine.createSpy(), flush: () => Promise.resolve(),
      setBuyNowItem: jasmine.createSpy().and.callFake((book: any, quantity: number) => cart.buyNowItem = {...book, quantity, stock: book.quantity})};
    router = {navigate: jasmine.createSpy()}; order = {placeOrder: jasmine.createSpy().and.returnValue(new Subject())};
    knowledge = {get: jasmine.createSpy().and.returnValue(of({settings: {shippingFee: 20000, freeShippingThreshold: 300000}}))};
    component = new CheckoutComponent(cart, order, {currentUser: {email: 'reader@example.com'}} as any,
      {refresh: () => Promise.resolve(), getAddresses: () => [], getDefault: () => undefined} as any,
      {getMethods: () => of({})} as any, router, {error: jasmine.createSpy(), success: jasmine.createSpy()} as any,
      {bump: jasmine.createSpy(), orderSuccess: () => Promise.resolve()} as any, knowledge, {refresh: () => of(catalog)} as any);
  });

  it('ưu tiên sách mua ngay đã lưu thay vì các dòng trong giỏ', async () => {
    cart.getBuyNowSelection.and.returnValue({productId: 9, quantity: 2});
    await component.ngOnInit();
    expect(component.cartItems).toEqual([jasmine.objectContaining({id: 9, quantity: 2, price: 50000})]);
    expect(component.buyNowMode).toBeTrue();
  });

  it('chỉ thanh toán các dòng giỏ hàng đã chọn', async () => {
    cart.getCartItems.and.returnValue([{id: 7, quantity: 1, selected: true}, {id: 9, quantity: 1, selected: false}]);
    await component.ngOnInit();
    expect(component.cartItems.map(item => item.id)).toEqual([7]);
  });

  it('giữ nháp mua ngay khi rời checkout chưa hoàn tất', () => {
    component.ngOnDestroy();
    expect(cart.clearBuyNowItem).not.toHaveBeenCalled();
  });

  it('chỉ hiển thị phương thức thanh toán đang khả dụng', () => {
    expect(component.availablePaymentMethods.map(method => method.code)).toEqual(['COD']);
    component.paymentMethods.find(method => method.code === 'MOMO').available = true;
    expect(component.availablePaymentMethods.map(method => method.code)).toEqual(['COD', 'MOMO']);
  });

  it('đưa người dùng về giỏ nếu không còn dòng hợp lệ', async () => {
    cart.getCartItems.and.returnValue([]);
    await component.ngOnInit();
    expect(router.navigate).toHaveBeenCalledWith(['/cart']);
  });

  it('chờ giỏ từ máy chủ trước khi kết luận giỏ trống', async () => {
    cart.getCartItems.and.returnValue([]);
    let finish: () => void = () => {};
    cart.flush = () => new Promise<void>(resolve => finish = resolve);
    const init = component.ngOnInit();
    expect(router.navigate).not.toHaveBeenCalled();
    cart.getCartItems.and.returnValue([{id: 7, quantity: 1, selected: true}]);
    finish(); await init;
    expect(component.cartItems.map(item => item.id)).toEqual([7]);
  });

  it('chỉ gửi một đơn khi bấm hai lần trong lúc đồng bộ giỏ', async () => {
    await component.ngOnInit();
    component.shippingForm = {label: '', name: 'Kiểm thử', phone: '0900000000', street: '12 Nguyễn Trãi, Hà Nội'};
    let finish: () => void = () => {};
    cart.flush = () => new Promise<void>(resolve => finish = resolve);
    const first = component.placeOrder(); await component.placeOrder();
    expect(order.placeOrder).not.toHaveBeenCalled();
    finish(); await first;
    expect(order.placeOrder).toHaveBeenCalledTimes(1);
  });

  it('tính phí từ chính sách hiện tại và miễn phí đúng ngưỡng', async () => {
    await component.ngOnInit();
    expect(knowledge.get).toHaveBeenCalled(); expect(component.getTotal()).toBe(120000);
    component.cartItems[0].quantity = 3;
    expect(component.getShipping()).toBe(0);
  });

  it('không tạo đơn khi chưa tải được chính sách giao hàng', async () => {
    knowledge.get.and.returnValue(throwError(new Error('offline')));
    await component.ngOnInit(); await component.placeOrder();
    expect(component.shippingUnavailable).toBeTrue();
    expect(component.formError).toContain('Chưa tải được chính sách giao hàng');
    expect(order.placeOrder).not.toHaveBeenCalled();
  });
});

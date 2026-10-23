import { of, Subject, throwError } from 'rxjs';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let component: CheckoutComponent;
  let cart: any, router: any, order: any, knowledge: any, payment: any, auth: any;
  const catalog = [{id: 7, name: 'Sách thử', quantity: 10, price: 100000}, {id: 9, name: 'Mua ngay', quantity: 10, price: 50000}];
  beforeEach(() => {
    cart = {buyNowItem: null, getCartItems: jasmine.createSpy().and.returnValue([{id: 7, quantity: 1, selected: true}]),
      getBuyNowSelection: jasmine.createSpy().and.returnValue(undefined), clearBuyNowItem: jasmine.createSpy(), refresh: jasmine.createSpy(), flush: () => Promise.resolve(),
      setBuyNowItem: jasmine.createSpy().and.callFake((book: any, quantity: number) => cart.buyNowItem = {...book, quantity, stock: book.quantity})};
    router = {navigate: jasmine.createSpy()}; order = {placeOrder: jasmine.createSpy().and.returnValue(new Subject())};
    knowledge = {get: jasmine.createSpy().and.returnValue(of({settings: {shippingFee: 20000, freeShippingThreshold: 300000}}))};
    payment = {getMethods: jasmine.createSpy().and.returnValue(of({})), start: jasmine.createSpy().and.returnValue(new Subject())};
    auth = {currentUser: {email: 'reader@example.com'}};
    component = new CheckoutComponent(cart, order, auth,
      {refresh: () => Promise.resolve(), getAddresses: () => [], getDefault: () => undefined} as any,
      payment, router, {error: jasmine.createSpy(), success: jasmine.createSpy()} as any,
      {bump: jasmine.createSpy(), orderSuccess: () => Promise.resolve()} as any, knowledge, {refresh: () => of(catalog)} as any);
  });
  afterEach(() => component.ngOnDestroy());

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

  it('giữ đủ bốn lựa chọn và chỉ cho chọn phương thức khả dụng', () => {
    component.loadPaymentMethods();
    expect(component.paymentMethods.map(method => method.code)).toEqual(['COD', 'BANK_QR', 'VNPAY', 'MOMO']);
    component.selectPayment(component.paymentMethods[3]);
    expect(component.selectedPayment).toBe('COD');
    payment.getMethods.and.returnValue(of({MOMO: {available: true}})); component.loadPaymentMethods();
    component.selectPayment(component.paymentMethods[3]); expect(component.selectedPayment).toBe('MOMO');
  });

  it('khóa thanh toán trực tuyến cũ khi tải lại bị lỗi và cho thử lại', () => {
    payment.getMethods.and.returnValue(of({VNPAY: {available: true}})); component.loadPaymentMethods();
    component.selectPayment(component.paymentMethods[2]);
    payment.getMethods.and.returnValue(throwError({status:503})); component.loadPaymentMethods();
    expect(component.selectedPayment).toBe('COD'); expect(component.methodsError).toContain('Chưa tải');
    expect(component.paymentMethods[2].available).toBeFalse();
    payment.getMethods.and.returnValue(of({VNPAY: {available:true}})); component.loadPaymentMethods();
    expect(component.methodsError).toBe(''); expect(component.paymentMethods[2].available).toBeTrue();
  });

  it('không tạo đơn khi phương thức chưa được xác minh', async () => {
    await component.ngOnInit(); component.selectedPayment = 'MOMO'; await component.placeOrder();
    expect(order.placeOrder).not.toHaveBeenCalled(); expect(component.formError).toContain('khả dụng');
  });

  it('giữ đúng đơn để thanh toán tiếp sau lỗi mở cổng, không cho tạo đơn lần nữa', async () => {
    payment.getMethods.and.returnValue(of({VNPAY: {available:true}}));
    payment.start.and.returnValue(throwError({status:503})); order.placeOrder.and.returnValue(of({ordersId:91}));
    await component.ngOnInit(); component.selectedPayment='VNPAY';
    component.shippingForm={label:'',name:'Người đọc',phone:'0900000000',street:'12 Nguyễn Trãi, Hà Nội'};
    await component.placeOrder(); await component.placeOrder();
    expect(order.placeOrder).toHaveBeenCalledTimes(1); expect(payment.start).toHaveBeenCalledOnceWith(91);
    expect(component.orderPlaced).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(['/payment/result'],{queryParams:{orderId:91}});
  });

  it('xóa thông tin giao hàng và dừng xử lý khi đổi tài khoản', async () => {
    await component.ngOnInit(); auth.currentUser.email='other@example.com'; component.ngDoCheck();
    expect(component.cartItems).toEqual([]); expect(component.shippingForm.phone).toBe('');
    expect(router.navigate).toHaveBeenCalledWith(['/login'],{queryParams:{returnUrl:'/checkout'}});
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

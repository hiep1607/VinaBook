import { shippingError, normalizePhone } from 'src/app/shared/shipping-validation';
import { ADDRESS_LABELS } from 'src/app/shared/address-labels';
import { Component, DoCheck, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ProductService } from 'src/app/services/product.service';
import { takeUntil, timeout } from 'rxjs/operators';
import { Subject } from 'rxjs';

import { AddressService } from 'src/app/services/address.service';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { NotificationService } from 'src/app/services/notification.service';
import { OrderService } from 'src/app/services/order.service';
import { PaymentMethodCode, PaymentMethodInfo, PaymentService } from 'src/app/services/payment.service';
import { MotionService } from 'src/app/services/motion.service';
import { StoreKnowledgeService, StoreSettings } from 'src/app/services/store-knowledge.service';

@Component({
  selector: 'app-checkout',
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit, DoCheck, OnDestroy {
  readonly addressLabels = ADDRESS_LABELS;

  @ViewChild('placeOrderButton') placeOrderButton?: ElementRef<HTMLButtonElement>;

  shippingSettings?: StoreSettings;
  shippingUnavailable = false;

  voucherCode='';voucherDiscount=0;voucherBusy=false;
  cartItems: any[] = [];
  addresses: any[] = [];
  selectedAddress: any = null;
  placing = false;
  preparing = false;
  loading = true;
  choosingAddress = false;
  buyNowMode = false;
  private destroyed = false;
  private owner = '';
  orderPlaced = false;
  mode = 'form';
  showNewForm = false;
  saveToProfile = false;
  formError = '';
  methodsLoading = true;
  methodsError = '';
  private stop$ = new Subject<void>();
  selectedPayment = 'COD';
  paymentMethods: (PaymentMethodInfo & { code: PaymentMethodCode; short: string })[] = [
    { code: 'COD', short: 'COD', label: 'Thanh toán khi nhận hàng', description: 'Thanh toán sau khi nhận và kiểm tra kiện sách.', available: true },
    { code: 'BANK_QR', short: 'QR', label: 'Chuyển khoản QR', description: 'Quét mã VietQR, đơn được xử lý sau khi cửa hàng xác nhận.', available: false },
    { code: 'VNPAY', short: 'VN', label: 'VNPay', description: 'Thẻ ngân hàng, tài khoản hoặc VNPAY-QR.', available: false },
    { code: 'MOMO', short: 'M', label: 'Ví MoMo', description: 'Thanh toán qua ví MoMo trên cổng MoMo Test.', available: false }
  ];

  shippingForm = {
    label: '',
    name: '',
    phone: '',
    street: ''
  };

  constructor(
    private cartService: CartService,
    private orderService: OrderService,
    private authService: AuthService,
    private addressService: AddressService,
    private paymentService: PaymentService,
    private router: Router,
    private notification: NotificationService,
    private motion: MotionService,
    private knowledge: StoreKnowledgeService,
    private products: ProductService
  ) {}

  async ngOnInit(): Promise<void> {
    this.owner = this.email;
    this.knowledge.get().subscribe(data => this.shippingSettings = data.settings, () => this.shippingUnavailable = true);
    this.shippingForm.name = this.authService.currentUser?.name || '';
    this.shippingForm.phone = this.authService.currentUser?.phone || '';
    try {
      this.cartService.getCartItems();
      await this.cartService.flush();
      const selection = this.cartService.getBuyNowSelection();
      const catalog = await this.products.refresh().pipe(timeout(20000)).toPromise();
      if (this.destroyed || this.owner !== this.email) return;
      if (selection) {
        const product = catalog.find(book => book.id === selection.productId);
        if (!product || product.quantity < selection.quantity) {
          this.formError = 'Sách vừa chọn không còn đủ số lượng. Bạn chọn lại số lượng hoặc sách khác nhé.';
          this.cartService.clearBuyNowItem();
          this.router.navigate(['/product', selection.productId]); return;
        }
        this.cartService.setBuyNowItem(product, selection.quantity);
        this.buyNowMode = true;
        this.cartItems = [{ ...this.cartService.buyNowItem }];
      } else {
        this.cartItems = this.cartService.getCartItems().filter(item => item.selected).map(item => {
          const product = catalog.find(book => book.id === item.id);
          return { ...item, price: product?.price ?? item.price, stock: product?.quantity ?? 0 };
        });
      }
      if (!this.cartItems.length) { this.router.navigate(['/cart']); return; }
      await this.loadAddresses();
      if (!this.destroyed) this.loadPaymentMethods();
    } catch (error) {
      if (!this.destroyed) this.formError = (error as any)?.error?.message || (error as any)?.message || 'Chưa tải được lựa chọn mua. Bạn thử tải lại trang nhé.';
    } finally { if (!this.destroyed) this.loading = false; }
  }

  maxQuantity(item: any): number { return Math.max(0, Math.min(99, item.stock ?? 99)); }
  get quantitiesValid(): boolean { return !!this.cartItems.length && this.cartItems.every(item => Number.isInteger(item.quantity) && item.quantity >= 1 && item.quantity <= this.maxQuantity(item)); }
  changeQuantity(item: any, value: any): void {
    if (this.placing || this.preparing || this.orderPlaced) return;
    const number = Number(value), max = this.maxQuantity(item);
    if (!Number.isInteger(number) || max < 1) { this.formError = 'Bạn chọn số lượng nguyên và sách còn hàng nhé.'; return; }
    const quantity = Math.max(1, Math.min(number, max));
    this.formError = quantity !== number ? 'Số lượng đã được điều chỉnh theo tồn kho hiện tại.' : '';
    this.cartItems = this.cartItems.map(line => line.id === item.id ? { ...line, quantity } : line);
    if (this.buyNowMode) this.cartService.setBuyNowItem({ ...item, quantity: item.stock }, quantity);
    else {
      const source = this.cartService.getCartItems().find(line => line.id === item.id);
      if (source) { source.quantity = quantity; this.cartService.saveCart(); }
    }
  }

  loadPaymentMethods(): void {
    if (this.destroyed || this.placing || this.orderPlaced) return;
    this.methodsLoading = true;
    this.methodsError = '';
    this.paymentService.getMethods().pipe(timeout(12000), takeUntil(this.stop$)).subscribe(
      methods => {
        this.paymentMethods = this.paymentMethods.map(method => ({
          ...method,
          available: method.code === 'COD' || methods[method.code]?.available === true,
          label: methods[method.code]?.label || method.label,
          description: methods[method.code]?.description || method.description
        }));
        if (!this.paymentMethods.some(method => method.code === this.selectedPayment && method.available)) this.selectedPayment = 'COD';
        this.methodsLoading = false;
      },
      () => {
        this.methodsLoading = false;
        this.methodsError = 'Chưa tải được các phương thức trực tuyến. Bạn có thể chọn trả tiền khi nhận hàng hoặc thử tải lại.';
        this.paymentMethods = this.paymentMethods.map(method => ({ ...method, available: method.code === 'COD' }));
        this.selectedPayment = 'COD';
      }
    );
  }

  selectPayment(method: any, event?: MouseEvent): void {
    if (method.available && !this.methodsLoading && !this.placing && !this.preparing && !this.orderPlaced) {
      this.selectedPayment = method.code;
      this.motion.bump(event?.currentTarget as HTMLElement | undefined);
    }
  }

  selectedPaymentLabel(): string {
    const selected = this.paymentMethods.find(method => method.code === this.selectedPayment);
    return selected ? selected.label : 'Thanh toán khi nhận hàng';
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.stop$.next();
    this.stop$.complete();
  }

  ngDoCheck(): void {
    if (this.owner && this.owner !== this.email) {
      this.ngOnDestroy();
      this.owner = '';
      this.cartItems = []; this.addresses = []; this.selectedAddress = null;
      this.shippingForm = { label: '', name: '', phone: '', street: '' };
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/checkout' } });
    }
  }

  private get email(): string {
    return this.authService.currentUser ? this.authService.currentUser.email : '';
  }

  async loadAddresses(): Promise<void> {
    const email = this.email;
    try { await this.addressService.refresh(email); if(this.destroyed || email !== this.email)return; this.addresses = this.addressService.getAddresses(email); } catch { if(this.destroyed || email !== this.email)return; this.addresses=[]; this.formError='Chưa tải được địa chỉ đã lưu. Bạn có thể nhập địa chỉ giao hàng bên dưới nhé.'; }

    if (this.addresses.length === 0) {
      this.mode = 'form';
      this.showNewForm = true;
      return;
    }

    this.mode = this.addresses.length === 1 ? 'single' : 'select';
    this.selectedAddress = this.addressService.getDefault(this.email) || this.addresses[0];
  }

  selectAddress(address: any): void {
    this.selectedAddress = address;
    this.choosingAddress = false;
  }

  useNewAddress(): void {
    this.showNewForm = true;
    this.formError = '';
    this.shippingForm = { label: '', name: this.authService.currentUser?.name || '', phone: this.authService.currentUser?.phone || '', street: '' };
  }

  cancelNewAddress(): void {
    this.showNewForm = false;
    this.formError = '';
  }

  getSubtotal(): number {
    return this.cartItems.reduce(
      (total, item) => total + Number(item.price) * item.quantity,
      0
    );
  }

  getShipping(): number {
    const subtotal = this.getSubtotal();
    return this.shippingSettings ? StoreKnowledgeService.shipping(subtotal, this.shippingSettings) : 0;
  }

  getTotal(): number {
    return this.getSubtotal() + this.getShipping() - this.voucherDiscount;
  }

  async placeOrder(): Promise<void> {
    if (!this.shippingSettings) { this.formError = 'Chưa tải được chính sách giao hàng. Vui lòng thử lại.'; return; }
    if (this.loading || this.placing || this.preparing || this.orderPlaced || this.voucherBusy) return;
    if (this.methodsLoading || !this.paymentMethods.some(method => method.code === this.selectedPayment && method.available)) {
      this.formError = 'Bạn chọn phương thức thanh toán đang khả dụng nhé.'; return;
    }
    if (!this.quantitiesValid) { this.formError = 'Bạn kiểm tra lại số lượng và tồn kho trước khi đặt hàng nhé.'; return; }
    this.preparing = true;
    try { await this.cartService.flush(); } catch { this.formError = this.cartService.syncError || 'Chưa đồng bộ được giỏ hàng. Vui lòng kiểm tra lại.'; return; }
    finally { this.preparing = false; }
    if (this.destroyed || this.owner !== this.email) return;
    this.formError = '';

    let shippingAddress: any;
    let isNewAddress = false;

    if (this.mode === 'form' || this.showNewForm) {
      const phone = normalizePhone(this.shippingForm.phone);
      this.formError=shippingError(this.shippingForm.name,phone,this.shippingForm.street);if(this.formError)return;
      if (!this.shippingForm.name.trim() || !phone || !this.shippingForm.street.trim()) {
        this.formError = 'Vui lòng nhập đầy đủ thông tin giao hàng';
        return;
      }
      if (!/^0\d{9}$/.test(phone)) {
        this.formError = 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0';
        return;
      }

      shippingAddress = {
        label: this.shippingForm.label.trim() || 'Địa chỉ',
        name: this.shippingForm.name.trim(),
        phone,
        street: this.shippingForm.street.trim()
      };
      isNewAddress = true;
    } else {
      if (!this.selectedAddress) {
        this.formError = 'Vui lòng chọn địa chỉ giao hàng';
        return;
      }
      shippingAddress = { ...this.selectedAddress };
    }

    this.formError=shippingError(shippingAddress.name,shippingAddress.phone,shippingAddress.street);if(this.formError)return;
    shippingAddress.phone=normalizePhone(shippingAddress.phone);
    const addressLine = `${shippingAddress.street} (Người nhận: ${shippingAddress.name})`;
    this.placing = true;

    this.orderService
      .placeOrder(this.email, addressLine, shippingAddress.phone, this.cartItems, this.selectedPayment, !this.buyNowMode, this.cartService.serverRevision, this.getTotal(), this.voucherCode, this.voucherDiscount).pipe(takeUntil(this.stop$))
      .subscribe(
        (order: any) => {
          if (this.destroyed || this.owner !== this.email) return;
          this.orderPlaced = true;
          if (isNewAddress && this.saveToProfile) {
            void this.addressService.addAddress(this.email, this.shippingForm).catch(()=>this.notification.error('Đơn đã tạo, nhưng chưa lưu được địa chỉ vào tài khoản. Bạn lưu lại trong Hồ sơ nhé.'));
          }
          if (!this.buyNowMode) {
            this.cartService.refresh();
          }
          this.cartService.clearBuyNowItem();
          if (this.selectedPayment === 'COD') {
            this.placing = false;
            this.notification.success('Đặt hàng thành công');
            this.motion.orderSuccess(this.placeOrderButton?.nativeElement)
              .then(() => { if (!this.destroyed) this.router.navigate(['/orders', order.ordersId]); });
            return;
          }

          this.paymentService.start(order.ordersId).pipe(timeout(25000), takeUntil(this.stop$)).subscribe(
            payment => {
              if (payment.redirectUrl) {
                window.location.assign(payment.redirectUrl);
                return;
              }
              this.placing = false;
              this.router.navigate(['/payment/result'], { queryParams: { orderId: order.ordersId } });
            },
            error => {
              this.placing = false;
              const message = error && error.error && error.error.message
                ? error.error.message
                : 'Không mở được cổng thanh toán';
              this.notification.error('Đơn đã được tạo. ' + message);
              this.router.navigate(['/payment/result'], { queryParams: { orderId: order.ordersId } });
            }
          );
        },
        error => {
          this.placing = false;
          this.formError = error?.error?.message || error?.message || 'Chưa xác nhận được kết quả đặt hàng. Vui lòng kiểm tra Đơn hàng trước khi đặt lại.';
          this.notification.error(this.formError);
          this.knowledge.get().subscribe(data => this.shippingSettings = data.settings, () => {});
        }
      );
  }
}

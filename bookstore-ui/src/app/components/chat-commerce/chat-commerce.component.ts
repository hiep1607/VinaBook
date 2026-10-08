import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { AddressService } from 'src/app/services/address.service';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { ChatAction, ChatProduct } from 'src/app/services/chatbot.service';
import { ChatCartItem, ChatCheckoutService, ChatQuote, ChatShipping, chatCartFingerprint } from 'src/app/services/chat-checkout.service';
import { PaymentService } from 'src/app/services/payment.service';
import { ProductService } from 'src/app/services/product.service';
import { StoreKnowledgeService, StoreSettings } from 'src/app/services/store-knowledge.service';

interface ShippingAddress { id: number; name: string; phone: string; street: string; isDefault?: boolean; }
interface CheckoutSelection { fromCart: boolean; product?: ChatProduct; quantity: number; }
interface PendingChange { action: ChatAction; product?: ChatProduct; fingerprint: string; }

@Component({
  selector: 'app-chat-commerce',
  templateUrl: './chat-commerce.component.html',
  styleUrls: ['./chat-commerce.component.scss']
})
export class ChatCommerceComponent implements OnDestroy, OnInit {
  @Output() busyChange = new EventEmitter<boolean>();
  @Output() updated = new EventEmitter<void>();
  @Output() added = new EventEmitter<number>();
  @Output() navigated = new EventEmitter<void>();

  visible = false;
  busy = false;
  error = '';
  notice = '';
  pending?: PendingChange;
  checkout?: CheckoutSelection;
  quote?: ChatQuote;
  placed?: { orderId: number; paymentMethod: string };
  settings?: StoreSettings;
  addresses: ShippingAddress[] = [];
  paymentMethods: { code: string; label: string }[] = [];
  voucherCode='';voucherDiscount=0;voucherBusy=false;
  get voucherItems():{id:number;quantity:number;price?:number}[]{return this.checkout?.fromCart?this.cartItems:this.checkout?.product?[{id:this.checkout.product.id,quantity:this.checkout.quantity,price:this.checkout.product.price}]:[];}
  form = { recipient: '', phone: '', street: '', paymentMethod: 'COD' };
  private catalog = new Map<number, ChatProduct>();
  private version = 0;
  private quoteCartKey = '';
  private failedConfirmation = false;
  recovery?: { token: string; paymentMethod: string };
  recoveryUnavailable = false;

  constructor(
    private cart: CartService,
    private auth: AuthService,
    private addressesService: AddressService,
    private checkoutService: ChatCheckoutService,
    private payments: PaymentService,
    private knowledge: StoreKnowledgeService,
    private products: ProductService,
    private router: Router
  ) {}

  ngOnInit(): void { this.readRecovery(); }
  private journalKey(owner = this.owner()): string { return 'vinabook_chat_pending_order:' + encodeURIComponent(owner); }
  private readRecovery(): void {
    this.recovery = undefined; this.recoveryUnavailable = false;
    try {
      const saved = JSON.parse(sessionStorage.getItem(this.journalKey()) || 'null');
      if (this.authenticated && saved && typeof saved.token === 'string' && /^[a-zA-Z0-9-]{1,80}$/.test(saved.token)) {
        this.recovery = saved; this.visible = true;
      }
    } catch {}
  }
  private rememberConfirmation(token: string, paymentMethod: string, owner: string): void {
    try { sessionStorage.setItem(this.journalKey(owner), JSON.stringify({ token, paymentMethod })); } catch {}
  }
  private forgetConfirmation(owner: string): void { try { sessionStorage.removeItem(this.journalKey(owner)); } catch {} }

  get cartItems(): ChatCartItem[] {
    return this.cart.getCartItems().map((item: ChatCartItem) => {
      const current = this.catalog.get(item.id);
      return { ...item, name: current?.name || item.name, image: current?.image || item.image,
        price: current?.price ?? item.price, stock: current?.quantity ?? 0 };
    });
  }
  get subtotal(): number { return this.cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0); }
  get shipping(): number { return this.settings ? StoreKnowledgeService.shipping(this.subtotal, this.settings) : 0; }
  get canCheckoutCart(): boolean {
    return !!this.settings && this.cartItems.length > 0 && this.cartItems.every(item =>
      Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= Math.min(99, item.stock || 0));
  }
  get authenticated(): boolean { return !!this.auth.currentUser?.token; }
  get pendingText(): string {
    if (!this.pending) return '';
    const { action, product } = this.pending;
    if (action.type === 'CLEAR_CART') return 'Bạn muốn xóa toàn bộ sách khỏi giỏ hàng? Mình chờ bạn xác nhận để tránh bỏ nhầm sách bạn vẫn muốn mua.';
    if (action.type === 'REMOVE_FROM_CART') return `Xóa “${product?.name}” khỏi giỏ hàng? Bạn kiểm tra tên sách rồi xác nhận để mình xóa đúng cuốn nhé.`;
    if (action.type === 'UPDATE_CART_QUANTITY') return `Đổi số lượng “${product?.name}” thành ${action.quantity} cuốn? Bạn kiểm tra lại số lượng rồi xác nhận nhé, để mình cập nhật đúng ý bạn.`;
    return `Thêm ${action.quantity || 1} cuốn “${product?.name}” vào giỏ hàng? Bạn xác nhận để mình thêm đúng cuốn và số lượng này nhé.`;
  }

  fingerprint(): string { return chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision); }
  private owner(): string { return this.auth.currentUser?.email || ''; }
  private current(version: number, owner: string): boolean { return this.version === version && this.owner() === owner; }
  private setBusy(value: boolean): void { this.busy = value; this.busyChange.emit(value); }
  private changed(): void { this.updated.emit(); }
  private failure(error: any): string {
    if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn nên mình chưa thể xác nhận kết quả cho tài khoản của bạn. Bạn đăng nhập lại và kiểm tra Đơn hàng trước khi đặt lại, để tránh tạo hai đơn nhé.';
    return error?.error?.message || error?.message || 'Mình chưa kết nối được với VinaBook nên chưa hoàn tất bước này. Bạn kiểm tra mạng rồi thử lại nhé.';
  }

  async showCart(): Promise<void> {
    if (this.busy || this.uncertainConfirmation) return;
    this.discard();
    this.visible = true;
    this.error = ''; this.notice = '';
    const version = this.version, owner = this.owner();
    this.setBusy(true); this.changed();
    try {
      await this.reload();
    } catch (error) {
      if (this.current(version, owner)) this.error = this.failure(error);
    } finally {
      if (this.current(version, owner)) { this.setBusy(false); this.changed(); }
    }
  }

  private async reload(): Promise<void> {
    // Refresh the same cart and catalog services used by storefront pages.
    this.cart.getCartItems();
    await this.cart.flush().catch(error => {
      this.cart.refresh();
      return this.cart.flush();
    });
    this.cart.refresh();
    const version = this.version, owner = this.owner();
    const [store, books] = await Promise.all([
      this.knowledge.getFresh().toPromise(), this.products.refresh().toPromise(), this.cart.flush()
    ]);
    if (!this.current(version, owner)) return;
    this.settings = store.settings;
    this.catalog = new Map(books.map(book => [book.id, book]));
    this.paymentMethods = Object.keys(store.paymentMethods).filter(code =>
      ['COD', 'BANK_QR', 'VNPAY', 'MOMO'].includes(code) && store.paymentMethods[code].available)
      .map(code => ({ code, label: store.paymentMethods[code].label }));
  }

  runAction(action: ChatAction, product?: ChatProduct, fingerprint?: string): void {
    if (this.busy || this.placed || this.uncertainConfirmation) return;
    if (action.type === 'SHOW_CART') { void this.showCart(); return; }
    if (this.checkout) { this.error = 'Bạn hoàn tất hoặc đóng bước đặt hàng trước khi sửa giỏ nhé, để sách trong đơn và tổng tiền không bị lệch với giỏ mới.'; return; }
    if (fingerprint && fingerprint !== this.fingerprint()) {
      this.visible = true; this.error = 'Giỏ đã thay đổi từ lúc bạn gửi câu hỏi nên mình cần bạn kiểm tra lại trước khi sửa. Bạn xem giỏ hiện tại rồi chọn thao tác mới nhé.'; this.changed(); return;
    }
    if (action.type === 'BUY_NOW' || action.type === 'CHECKOUT_CART') {
      void this.startCheckout(action.type === 'CHECKOUT_CART', product, action.quantity || 1, fingerprint); return;
    }
    if (!['ADD_TO_CART', 'REMOVE_FROM_CART', 'UPDATE_CART_QUANTITY', 'CLEAR_CART'].includes(action.type)) return;
    this.visible = true; this.error = ''; this.notice = '';
    this.pending = { action, product, fingerprint: this.fingerprint() };
    this.changed();
  }

  requestQuantity(item: ChatCartItem, event: Event): void {
    const input = event.target as HTMLInputElement;
    const quantity = Number(input.value); input.value = String(item.quantity);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, item.stock || 0)) {
      this.error = 'Bạn chọn số lượng từ 1 đến 99 và không vượt số sách còn bán nhé, để mình chuẩn bị đủ sách cho bạn.'; return;
    }
    if (quantity === item.quantity) return;
    this.runAction({ type: 'UPDATE_CART_QUANTITY', label: 'Đổi số lượng', productId: item.id, quantity, requiresConfirmation: true },
      this.catalog.get(item.id));
  }
  requestRemoval(item: ChatCartItem): void {
    const product = this.catalog.get(item.id) || { ...item, quantity: item.stock || 0 } as ChatProduct;
    this.runAction({ type: 'REMOVE_FROM_CART', label: 'Xóa sách', productId: item.id, requiresConfirmation: true }, product);
  }
  requestClear(): void { this.runAction({ type: 'CLEAR_CART', label: 'Xóa giỏ', requiresConfirmation: true }); }
  cancelChange(): void { if (!this.busy) this.pending = undefined; }

  async confirmChange(): Promise<void> {
    if (!this.pending || this.busy) return;
    const pending = this.pending, version = this.version, owner = this.owner();
    this.setBusy(true); this.error = ''; this.notice = '';
    try {
      await this.cart.flush();
      if (!this.current(version, owner)) return;
      if (pending.fingerprint !== this.fingerprint()) throw new Error('Giỏ đã thay đổi nên thao tác vừa chọn không còn khớp. Bạn xem lại giỏ rồi chọn thao tác cần thực hiện nhé.');
      const { action, product } = pending;
      const item = this.cart.getCartItems().find(row => row.id === action.productId);
      const quantity = action.quantity || 1;
      if (action.type === 'ADD_TO_CART') {
        const total = (item?.quantity || 0) + quantity;
        if (!product || !Number.isInteger(quantity) || quantity < 1 || total > Math.min(99, product.quantity))
          throw new Error('Số lượng thêm vượt số sách còn bán nên mình chưa thể thêm đủ vào giỏ. Bạn kiểm tra lại và chọn số lượng ít hơn nhé.');
        this.cart.addToCart(product, quantity);
      } else if (action.type === 'CLEAR_CART') {
        await this.cart.clearCart();
      } else {
        if (!item) throw new Error('Sách không còn trong giỏ nên mình chưa thể sửa cuốn này. Bạn tải lại giỏ để xem danh sách hiện tại nhé.');
        if (action.type === 'REMOVE_FROM_CART') this.cart.removeItem(item);
        else {
          if (!Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, product?.quantity || 0))
            throw new Error('Số lượng mới vượt số sách còn bán. Bạn chọn số lượng ít hơn nhé, để mình cập nhật giỏ với số sách hiện có.');
          item.quantity = quantity; this.cart.saveCart();
        }
      }
      await this.cart.flush();
      if (!this.current(version, owner)) return;
      this.pending = undefined;
      this.notice = 'Mình đã cập nhật giỏ hàng theo lựa chọn của bạn. Bạn có thể kiểm tra tổng tiền bên dưới nhé.';
      if (action.type === 'ADD_TO_CART' && action.productId) this.added.emit(action.productId);
      // A confirmed mutation is never repeated because a subsequent catalog read fails.
      try { await this.reload(); } catch { this.error = 'Giỏ đã cập nhật, nhưng mình chưa tải được giá và phí giao mới. Bạn tải lại giỏ để kiểm tra tổng tiền trước khi đặt hàng nhé.'; }
    } catch (error) {
      if (this.current(version, owner)) {
        this.pending = undefined;
        this.error = this.failure(error) + ' Tải lại giỏ để kiểm tra kết quả trước khi sửa tiếp.';
      }
    } finally {
      if (this.current(version, owner)) { this.setBusy(false); this.changed(); }
    }
  }

  async startCheckout(fromCart: boolean, product?: ChatProduct, quantity = 1, fingerprint?: string): Promise<void> {
    if (this.busy || this.uncertainConfirmation) return;
    this.visible = true; this.error = ''; this.notice = ''; this.pending = undefined;
    if (!this.authenticated) { this.error = 'Mình có thể giúp bạn chuẩn bị đơn, nhưng bạn cần đăng nhập để đơn được lưu đúng tài khoản. Sau khi đăng nhập, hãy kiểm tra lại giỏ của tài khoản rồi tiếp tục nhé.'; this.changed(); return; }
    const version = this.version, owner = this.owner();
    this.setBusy(true); this.changed();
    try {
      await this.reload();
      if (!this.current(version, owner)) return;
      if (fromCart && fingerprint && fingerprint !== this.fingerprint()) throw new Error('Giỏ đã thay đổi nên mình cần kiểm tra lại sách và tổng tiền. Bạn xem lại giỏ trước khi đặt hàng nhé.');
      if (fromCart && !this.canCheckoutCart) throw new Error('Giỏ trống hoặc có sách không đủ tồn kho nên mình chưa thể chuẩn bị đơn. Bạn sửa giỏ hoặc giảm số lượng trước khi đặt hàng nhé.');
      if (!fromCart) {
        product = product && this.catalog.get(product.id);
        if (!product || !Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, product.quantity))
          throw new Error('Sách không còn đủ số lượng bạn muốn đặt. Bạn chọn lại số lượng ít hơn để mình kiểm tra và tính tổng tiền nhé.');
      }
      this.checkout = { fromCart, product, quantity };
      this.placed = undefined; this.failedConfirmation = false;
      await this.addressesService.refresh(owner);
      if (!this.current(version, owner)) return;
      this.addresses = this.addressesService.getAddresses(owner);
      const saved = this.addresses.find(address => address.isDefault) || this.addresses[0];
      this.form = { recipient: saved?.name || this.auth.currentUser?.name || '', phone: saved?.phone || this.auth.currentUser?.phone || '',
        street: saved?.street || '', paymentMethod: this.paymentMethods[0]?.code || 'COD' };
    } catch (error) { if (this.current(version, owner)) this.error = this.failure(error); }
    finally { if (this.current(version, owner)) { this.setBusy(false); this.changed(); } }
  }

  useAddress(id: string): void {
    const address = this.addresses.find(saved => saved.id === Number(id));
    if (address) this.form = { ...this.form, recipient: address.name, phone: address.phone, street: address.street };
  }
  async requestQuote(): Promise<void> {
    if (!this.checkout || this.busy || this.quote || this.voucherBusy) return;
    const form = { recipient: this.form.recipient.trim(), phone: this.form.phone.trim(), street: this.form.street.trim(), paymentMethod: this.form.paymentMethod };
    if (!form.recipient || form.recipient.length > 80 || !/^0\d{9}$/.test(form.phone) || !form.street || form.street.length > 160) {
      this.error = 'Bạn điền tên người nhận, số điện thoại 10 chữ số bắt đầu bằng 0 và địa chỉ đầy đủ nhé. Mình cần các thông tin này để chuẩn bị giao sách đúng người, đúng nơi.'; return;
    }
    const selection = this.checkout, version = this.version, owner = this.owner();
    this.setBusy(true); this.error = ''; this.notice = '';
    try {
      await this.cart.flush();
      if (!this.current(version, owner)) return;
      const input: ChatShipping = { ...form, voucherCode:this.voucherCode, fromCart: selection.fromCart, cartRevision: this.cart.serverRevision };
      if (selection.fromCart) input.items = this.cart.getCartItems().map(item => ({ productId: item.id, quantity: item.quantity }));
      else { input.productId = selection.product!.id; input.quantity = selection.quantity; }
      if (selection.fromCart && !input.items?.length) throw new Error('Giỏ hàng đã trống nên chưa có sách để tính tổng tiền. Bạn chọn lại sách rồi mình tiếp tục chuẩn bị đơn nhé.');
      const cartKey = this.fingerprint();
      const quote = await this.checkoutService.quote(input).toPromise();
      if (!this.current(version, owner)) return;
      this.quote = quote; this.quoteCartKey = cartKey; this.failedConfirmation = false;
    } catch (error) { if (this.current(version, owner)) this.error = this.failure(error); }
    finally { if (this.current(version, owner)) { this.setBusy(false); this.changed(); } }
  }

  editQuote(): void {
    if (this.busy || this.failedConfirmation) return;
    const token = this.quote?.token;
    this.quote = undefined; this.error = '';
    if (token) this.checkoutService.cancelDraft(token).subscribe({ error: () => {} });
  }

  async confirmOrder(): Promise<void> {
    if (!this.quote || !this.checkout || this.busy || this.placed) return;
    const quote = this.quote, selection = this.checkout, version = this.version, owner = this.owner();
    let submitted = false;
    this.setBusy(true); this.error = ''; this.notice = '';
    try {
      if (!this.failedConfirmation) await this.cart.flush();
      if (!this.current(version, owner)) return;
      if (!this.failedConfirmation && selection.fromCart && this.quoteCartKey !== this.fingerprint()) throw new Error('Giỏ đã thay đổi nên báo giá cũ có thể không còn khớp. Bạn xem lại báo giá trước khi xác nhận nhé.');
      this.rememberConfirmation(quote.token, quote.paymentMethod, owner);
      submitted = true;
      const result = await this.checkoutService.confirm(quote.token).toPromise();
      if (!this.current(version, owner)) return;
      this.placed = { orderId: result.orderId, paymentMethod: quote.paymentMethod };
      this.forgetConfirmation(owner); this.failedConfirmation = false;
      this.quote = undefined; this.checkout = undefined; this.form = { recipient: '', phone: '', street: '', paymentMethod: 'COD' };
      this.notice = `Mình đã tạo đơn #${result.orderId} theo xác nhận của bạn. Bạn có thể mở Đơn hàng để theo dõi nhé.`;
      if (selection.fromCart) { this.cart.refresh(); await this.cart.flush().catch(() => { this.error = 'Đơn đã được tạo, nhưng mình chưa tải lại được giỏ hàng. Bạn tải lại giỏ để xem những sách còn lại nhé.'; }); }
    } catch (error) {
      if (this.current(version, owner)) {
        this.error = this.failure(error);
        if (!submitted || [400, 409, 404].includes((error as any)?.status)) {
          this.forgetConfirmation(owner);
          this.failedConfirmation = false; this.quote = undefined;
        } else {
          // Keep the same token after an uncertain response. Retrying can recover the
          // committed order; creating a new quote could instead create a second order.
          this.failedConfirmation = true;
          if ((error as any)?.status === 410) this.recoveryUnavailable = true;
          this.error += ' Mình chưa xác nhận được kết quả đặt hàng. Bạn kiểm tra Đơn hàng hoặc kiểm tra lại bằng cùng báo giá này nhé, để tránh tạo thêm một đơn trùng.';
        }
      }
    } finally {
      if (this.current(version, owner)) { this.setBusy(false); this.changed(); }
    }
    if (this.current(version, owner) && this.placed && this.placed.paymentMethod !== 'COD') await this.startPayment();
  }

  async startPayment(): Promise<void> {
    if (this.busy || !this.placed || this.placed.paymentMethod === 'COD') return;
    const placed = this.placed, version = this.version, owner = this.owner();
    this.setBusy(true); this.error = '';
    try {
      const payment = await this.payments.start(placed.orderId).toPromise();
      if (!this.current(version, owner)) return;
      if (payment.redirectUrl) { window.location.assign(payment.redirectUrl); return; }
      this.router.navigate(['/payment/result'], { queryParams: { orderId: placed.orderId } }); this.navigated.emit();
    } catch (error) { if (this.current(version, owner)) this.error = 'Đơn đã được tạo, nhưng mình chưa mở được bước thanh toán. ' + this.failure(error) + ' Bạn có thể tiếp tục thanh toán đơn này bằng nút bên dưới; không cần đặt lại.'; }
    finally { if (this.current(version, owner)) { this.setBusy(false); this.changed(); } }
  }

  async recoverOrder(): Promise<void> {
    if (this.busy || !this.recovery) return;
    const recovery = this.recovery, version = this.version, owner = this.owner();
    this.setBusy(true); this.error = '';
    try {
      const result = await this.checkoutService.confirm(recovery.token).toPromise();
      if (!this.current(version, owner)) return;
      this.placed = { orderId: result.orderId, paymentMethod: recovery.paymentMethod };
      this.recovery = undefined; this.forgetConfirmation(owner);
      this.cart.refresh(); await this.cart.flush().catch(() => { this.error = 'Mình đã tìm lại được đơn, nhưng chưa tải lại được giỏ. Bạn tải lại giỏ để xem danh sách hiện tại nhé.'; });
    } catch (error) {
      if (this.current(version, owner)) {
        this.error = this.failure(error) + ' Bạn kiểm tra Đơn hàng trước khi bắt đầu đơn mới nhé, để tránh đặt trùng sách.';
        this.recoveryUnavailable = [404, 409, 410].includes((error as any)?.status);
      }
    } finally { if (this.current(version, owner)) { this.setBusy(false); this.changed(); } }
    if (this.current(version, owner) && this.placed && this.placed.paymentMethod !== 'COD') await this.startPayment();
  }
  acknowledgeOrders(): void {
    if (this.busy || !this.recoveryUnavailable) return;
    this.forgetConfirmation(this.owner()); this.recovery = undefined; this.failedConfirmation = false;
    this.recoveryUnavailable = false; this.discard(); void this.showCart();
  }
  get uncertainConfirmation(): boolean { return this.failedConfirmation || !!this.recovery; }
  paymentLabel(code: string): string { return this.paymentMethods.find(method => method.code === code)?.label || code; }
  navigate(route: string): void { if (!this.busy) { this.router.navigateByUrl(route); this.navigated.emit(); } }
  dismiss(): void { if (!this.busy && !this.uncertainConfirmation) { this.discard(); this.visible = false; this.changed(); } }
  reset(): void {
    this.version++;
    this.discard(); this.visible = false; this.error = ''; this.notice = ''; this.settings = undefined;
    this.catalog.clear(); this.setBusy(false);
    this.readRecovery();
  }
  private discard(): void {
    const token = this.quote?.token;
    if (token && !this.failedConfirmation) this.checkoutService.cancelDraft(token).subscribe({ error: () => {} });
    this.quote = undefined; this.checkout = undefined; this.placed = undefined; this.pending = undefined;
    this.failedConfirmation = false; this.addresses = [];this.voucherCode='';this.voucherDiscount=0;this.voucherBusy=false;
    this.form = { recipient: '', phone: '', street: '', paymentMethod: 'COD' };
  }
  ngOnDestroy(): void { this.version++; this.discard(); }
}

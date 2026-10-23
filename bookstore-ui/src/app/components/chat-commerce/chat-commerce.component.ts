import { shippingError, normalizePhone } from 'src/app/shared/shipping-validation';
import { Component, DoCheck, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { AddressService } from 'src/app/services/address.service';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { ChatAction, ChatProduct } from 'src/app/services/chatbot.service';
import { ChatCartItem, ChatCheckoutService, ChatPurchaseIntent, ChatQuote, ChatShipping, chatCartFingerprint } from 'src/app/services/chat-checkout.service';
import { PaymentService } from 'src/app/services/payment.service';
import { ProductService } from 'src/app/services/product.service';
import { StoreKnowledgeService, StoreSettings } from 'src/app/services/store-knowledge.service';

interface ShippingAddress { id: number; label?: string; name: string; phone: string; street: string; isDefault?: boolean; }
interface CheckoutSelection { fromCart: boolean; product?: ChatProduct; quantity: number; }
interface PendingChange { action: ChatAction; product?: ChatProduct; fingerprint: string; }
interface UndoChange { items: ChatCartItem[]; fingerprint: string; owner: string; }

@Component({
  selector: 'app-chat-commerce',
  templateUrl: './chat-commerce.component.html',
  styleUrls: ['./chat-commerce.component.scss']
})
export class ChatCommerceComponent implements OnDestroy, OnInit, DoCheck {
  @Output() busyChange = new EventEmitter<boolean>();
  @Output() updated = new EventEmitter<void>();
  @Output() added = new EventEmitter<number>();
  @Output() undone = new EventEmitter<void>();
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
  selectedAddressId = '';
  editingAddress = true;
  saveDeliveryAddress = false;
  paymentMethods: { code: string; label: string }[] = [];
  voucherCode='';voucherDiscount=0;voucherBusy=false;
  quoteUpdating = false;
  quantityError = '';
  private quoteVersion = 0;
  private quoteTimer?: ReturnType<typeof setTimeout>;
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private quoteTask?: Promise<void>;
  private quoteQueued = false;
  private quoteInputKey = '';
  private observedInputKey = '';
  private undo?: UndoChange;
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
  ngDoCheck(): void {
    if (this.checkout && !this.busy && !this.uncertainConfirmation && this.authenticated
        && this.observedInputKey !== this.inputKey()) this.scheduleQuote();
  }
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
        price: current?.price ?? item.price, stock: current?.quantity ?? (this.settings ? 0 : undefined) };
    });
  }
  get subtotal(): number { return this.cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0); }
  get shipping(): number { return this.settings ? StoreKnowledgeService.shipping(this.subtotal, this.settings) : 0; }
  get canCheckoutCart(): boolean {
    return !!this.settings && this.cartItems.length > 0 && this.cartItems.every(item =>
      Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= Math.min(99, item.stock || 0));
  }
  get authenticated(): boolean { return !!this.auth.currentUser?.token; }
  get checkoutItems(): ChatCartItem[] {
    if (!this.checkout) return [];
    if (this.checkout.fromCart) return this.cartItems;
    const product = this.checkout.product;
    return product ? [{ ...product, quantity: this.checkout.quantity, stock: product.quantity }] : [];
  }
  get checkoutSubtotal(): number { return this.checkoutItems.reduce((sum, item) => sum + item.price * item.quantity, 0); }
  get checkoutShipping(): number { return this.settings ? StoreKnowledgeService.shipping(this.checkoutSubtotal, this.settings) : 0; }
  get totalEstimated(): boolean { return !this.quote || this.quoteInputKey !== this.inputKey() || this.quoteUpdating; }
  get checkoutTotal(): number { return this.totalEstimated ? this.checkoutSubtotal + this.checkoutShipping : this.quote!.total; }
  get inputValid(): boolean {
    return !!this.checkout && this.authenticated && !!this.settings && !this.quantityError
      && !shippingError(this.form.recipient,this.form.phone,this.form.street)
      && this.paymentMethods.some(method => method.code === this.form.paymentMethod)
      && (!this.voucherCode.trim() || /^[A-Z0-9_-]{3,32}$/.test(this.voucherCode.trim().toUpperCase()))
      && this.checkoutItems.length > 0 && this.checkoutItems.every(item => Number.isInteger(item.quantity)
        && item.quantity > 0 && item.quantity <= Math.min(99, item.stock || 0));
  }
  get canConfirmOrder(): boolean {
    return !this.busy && !this.quoteUpdating && !this.placed && !!this.quote && this.inputValid
      && this.quoteInputKey === this.inputKey() && this.quote.expiresAt > Date.now()
      && (!this.checkout?.fromCart || this.quoteCartKey === this.fingerprint());
  }
  get canUndo(): boolean { return !!this.undo && this.undo.owner === this.owner() && !this.busy && !this.uncertainConfirmation && !this.checkout; }
  private inputKey(): string {
    return JSON.stringify([this.checkout?.fromCart, this.checkoutItems.map(item => [item.id, item.quantity, item.price]),
      this.checkout?.fromCart ? this.cart.serverRevision : null, this.form.recipient.trim(), this.form.phone.trim(),
      this.form.street.trim(), this.form.paymentMethod, this.voucherCode.trim().toUpperCase()]);
  }
  get pendingText(): string {
    if (!this.pending) return '';
    const { action, product } = this.pending;
    if (action.type === 'CLEAR_CART') return 'Bạn muốn xóa toàn bộ sách khỏi giỏ hàng? Mình chờ bạn xác nhận để tránh bỏ nhầm sách bạn vẫn muốn mua.';
    if (action.type === 'REMOVE_FROM_CART') return `Xóa “${product?.name}” khỏi giỏ hàng? Bạn kiểm tra tên sách rồi xác nhận để mình xóa đúng cuốn nhé.`;
    if (action.type === 'UPDATE_CART_QUANTITY') return `Đổi số lượng “${product?.name}” thành ${action.quantity} cuốn? Bạn kiểm tra lại số lượng rồi xác nhận nhé, để mình cập nhật đúng ý bạn.`;
    return `Thêm ${action.quantity || 1} cuốn “${product?.name}” vào giỏ hàng? Bạn xác nhận để mình thêm đúng cuốn và số lượng này nhé.`;
  }

  fingerprint(): string { return chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision); }
  trackByProductId(_index: number, item: ChatCartItem): number { return item.id; }
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

  runAction(action: ChatAction, product?: ChatProduct, fingerprint?: string, direct = false): void {
    if (this.busy || this.placed || this.uncertainConfirmation) return;
    if (action.type === 'SHOW_CART') { void this.showCart(); return; }
    if (this.checkout) { this.error = 'Bạn hoàn tất hoặc đóng bước đặt hàng trước khi sửa giỏ nhé, để sách trong đơn và tổng tiền không bị lệch với giỏ mới.'; return; }
    if (action.type !== 'BUY_NOW' && fingerprint && fingerprint !== this.fingerprint()) {
      this.visible = true; this.error = 'Giỏ đã thay đổi từ lúc bạn gửi câu hỏi nên mình cần bạn kiểm tra lại trước khi sửa. Bạn xem giỏ hiện tại rồi chọn thao tác mới nhé.'; this.changed(); return;
    }
    if (action.type === 'BUY_NOW' || action.type === 'CHECKOUT_CART') {
      void this.startCheckout(action.type === 'CHECKOUT_CART', product, action.quantity || 1, fingerprint); return;
    }
    if (!['ADD_TO_CART', 'REMOVE_FROM_CART', 'UPDATE_CART_QUANTITY', 'CLEAR_CART'].includes(action.type)) return;
    this.visible = true; this.error = ''; this.notice = '';
    this.pending = { action, product, fingerprint: this.fingerprint() };
    this.changed();
    if (direct && ['ADD_TO_CART', 'UPDATE_CART_QUANTITY'].includes(action.type)) void this.confirmChange();
  }

  requestQuantity(item: ChatCartItem, event: Event): void {
    const input = event.target as HTMLInputElement;
    const quantity = Number(input.value); input.value = String(item.quantity);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, item.stock || 0)) {
      this.error = 'Bạn chọn số lượng từ 1 đến 99 và không vượt số sách còn bán nhé, để mình chuẩn bị đủ sách cho bạn.'; return;
    }
    if (quantity === item.quantity) return;
    this.runAction({ type: 'UPDATE_CART_QUANTITY', label: 'Đổi số lượng', productId: item.id, quantity, requiresConfirmation: true },
      this.catalog.get(item.id), undefined, true);
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
    const before = this.cart.getCartItems().map(item => ({ ...item }));
    this.undo = undefined;
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
      const afterKey = this.fingerprint();
      if (['ADD_TO_CART', 'UPDATE_CART_QUANTITY'].includes(action.type)) this.undo = { items: before, fingerprint: afterKey, owner };
      if (action.type === 'ADD_TO_CART' && action.productId) this.added.emit(action.productId);
      // A confirmed mutation is never repeated because a subsequent catalog read fails.
      try { await this.reload(); } catch { this.error = 'Giỏ đã cập nhật, nhưng mình chưa tải được giá và phí giao mới. Bạn tải lại giỏ để kiểm tra tổng tiền trước khi đặt hàng nhé.'; }
      if (this.undo && this.undo.fingerprint !== this.fingerprint()) this.undo = undefined;
    } catch (error) {
      if (this.current(version, owner)) {
        this.pending = undefined;
        this.error = this.failure(error) + ' Tải lại giỏ để kiểm tra kết quả trước khi sửa tiếp.';
      }
    } finally {
      if (this.current(version, owner)) { this.setBusy(false); this.changed(); }
    }
  }

  async undoLastChange(): Promise<void> {
    if (!this.canUndo || !this.undo) return;
    const undo = this.undo, version = this.version, owner = this.owner();
    this.setBusy(true); this.error = ''; this.notice = '';
    try {
      await this.cart.flush();
      if (!this.current(version, owner)) return;
      if (undo.owner !== owner || undo.fingerprint !== this.fingerprint()) throw new Error('Giỏ đã thay đổi. Mình giữ giỏ hiện tại để tránh ghi đè lựa chọn mới của bạn.');
      const items = this.cart.getCartItems();
      items.splice(0, items.length, ...undo.items.map(item => ({ ...item })));
      this.cart.saveCart(); await this.cart.flush();
      if (!this.current(version, owner)) return;
      this.undo = undefined; this.notice = 'Đã hoàn tác thay đổi vừa rồi.'; this.undone.emit();
      await this.reload();
    } catch (error) { if (this.current(version, owner)) { this.undo = undefined; this.error = this.failure(error); } }
    finally { if (this.current(version, owner)) { this.setBusy(false); this.changed(); } }
  }

  async startCheckout(fromCart: boolean, product?: ChatProduct, quantity = 1, fingerprint?: string): Promise<void> {
    if (this.busy || this.uncertainConfirmation) return;
    this.clearQuoteWork();
    this.visible = true; this.error = ''; this.notice = ''; this.pending = undefined;
    this.checkout = { fromCart, product, quantity };
    this.quote = undefined; this.placed = undefined; this.undo = undefined;
    this.quantityError = ''; this.voucherCode = ''; this.voucherDiscount = 0;
    this.saveDeliveryAddress = false;
    if (!this.authenticated) { this.error = ''; this.changed(); return; }
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
      this.addresses = [...this.addressesService.getAddresses(owner)]
        .sort((a, b) => Number(!!b.isDefault) - Number(!!a.isDefault));
      this.form = { recipient: '', phone: '', street: '', paymentMethod: this.paymentMethods[0]?.code || 'COD' };
      this.useAddress(this.addresses[0]?.id.toString() || '');
    } catch (error) { if (this.current(version, owner)) { this.checkout = undefined; this.error = this.failure(error); } }
    finally { if (this.current(version, owner)) { this.setBusy(false); if (this.checkout) this.scheduleQuote(0); this.changed(); } }
  }

  login(): void {
    if (!this.checkout || this.busy || this.uncertainConfirmation) return;
    this.checkoutService.rememberPurchaseIntent({ fromCart: this.checkout.fromCart, productId: this.checkout.product?.id,
      quantity: this.checkout.quantity, returnUrl: this.router.url || '/' });
    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url || '/' } }); this.navigated.emit();
  }
  async resumePurchase(intent: ChatPurchaseIntent): Promise<void> {
    if (!this.authenticated || this.uncertainConfirmation) return;
    await this.startCheckout(intent.fromCart, intent.productId ? { id: intent.productId } as ChatProduct : undefined, intent.quantity);
  }

  useAddress(id: string): void {
    const address = this.addresses.find(saved => saved.id === Number(id));
    this.selectedAddressId = address ? address.id.toString() : '';
    this.editingAddress = !address;
    this.saveDeliveryAddress = false;
    this.form = { ...this.form, recipient: address?.name || this.auth.currentUser?.name || '',
      phone: address?.phone || this.auth.currentUser?.phone || '', street: address?.street || '' };
    if (!this.busy) this.scheduleQuote();
  }
  editAddress(): void { if (this.busy || this.uncertainConfirmation) return; this.selectedAddressId = ''; this.editingAddress = true; }
  deliveryChanged(): void { if (!this.busy && !this.uncertainConfirmation) this.scheduleQuote(); }

  async changeCheckoutQuantity(productId: number, rawValue: any): Promise<void> {
    if (!this.checkout || this.busy || this.uncertainConfirmation) return;
    const item = this.checkoutItems.find(row => row.id === productId), quantity = Number(rawValue);
    if (!item || !Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, item.stock || 0)) {
      this.quantityError = 'Số lượng phải từ 1 đến 99 và không vượt số sách còn bán.'; this.scheduleQuote(); return;
    }
    this.quantityError = '';
    if (!this.checkout.fromCart) {
      this.checkout.quantity = quantity; this.scheduleQuote(); return;
    }
    const row = this.cart.getCartItems().find(line => line.id === productId);
    if (!row || row.quantity === quantity) { this.scheduleQuote(); return; }
    const version = this.version, owner = this.owner();
    this.setBusy(true);
    try {
      row.quantity = quantity; this.cart.saveCart();
      // The local totals change synchronously; the quote waits for the saved revision.
      this.scheduleQuote();
      await this.cart.flush();
      if (this.current(version, owner)) this.error = '';
    } catch (error) { if (this.current(version, owner)) this.error = this.failure(error); }
    finally { if (this.current(version, owner)) { this.setBusy(false); this.scheduleQuote(0); } }
  }

  private clearQuoteWork(): void {
    this.quoteVersion++; this.quoteQueued = false;
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    if (this.expiryTimer) clearTimeout(this.expiryTimer);
    this.quoteTimer = undefined; this.expiryTimer = undefined;
    this.quoteUpdating = false; this.quoteInputKey = ''; this.observedInputKey = '';
  }
  private scheduleQuote(delay = 250): void {
    if (this.uncertainConfirmation) return;
    this.quoteVersion++;
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    if (this.expiryTimer) clearTimeout(this.expiryTimer);
    this.quoteTimer = undefined; this.expiryTimer = undefined;
    this.quote = undefined; this.quoteInputKey = ''; this.voucherDiscount = 0;
    this.observedInputKey = this.inputKey();
    this.quoteUpdating = this.inputValid;
    if (!this.inputValid || this.busy) { this.quoteUpdating = false; return; }
    if (this.quoteTask) { this.quoteQueued = true; return; }
    this.quoteTimer = setTimeout(() => { this.quoteTimer = undefined; void this.requestQuote(); }, delay);
  }

  async requestQuote(): Promise<void> {
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    this.quoteTimer = undefined;
    if (!this.checkout || this.busy || this.uncertainConfirmation) return;
    if (this.quoteTask) { this.quoteQueued = true; return this.quoteTask; }
    if (this.quote && this.quoteInputKey === this.inputKey() && this.quote.expiresAt > Date.now()) return;
    const form = { recipient: this.form.recipient.trim(), phone: normalizePhone(this.form.phone), street: this.form.street.trim(), paymentMethod: this.form.paymentMethod };
    if (!this.inputValid) {
      this.quote = undefined; this.quoteUpdating = false;
      this.error = this.quantityError || 'Bạn bổ sung người nhận, số điện thoại 10 chữ số bắt đầu bằng 0 và địa chỉ để xác nhận đơn nhé.'; return;
    }
    const selection = this.checkout, version = this.version, owner = this.owner(), revision = this.quoteVersion;
    this.quote = undefined; this.quoteUpdating = true; this.error = ''; this.notice = '';
    const work = async () => { try {
      await this.cart.flush();
      if (!this.current(version, owner) || revision !== this.quoteVersion || !this.inputValid) return;
      const input: ChatShipping = { ...form, voucherCode: this.voucherCode.trim().toUpperCase(), fromCart: selection.fromCart, cartRevision: this.cart.serverRevision };
      if (selection.fromCart) input.items = this.cart.getCartItems().map(item => ({ productId: item.id, quantity: item.quantity }));
      else { input.productId = selection.product!.id; input.quantity = selection.quantity; }
      if (selection.fromCart && !input.items?.length) throw new Error('Giỏ hàng đã trống nên chưa có sách để tính tổng tiền. Bạn chọn lại sách rồi mình tiếp tục chuẩn bị đơn nhé.');
      const cartKey = this.fingerprint(), inputKey = this.inputKey();
      this.observedInputKey = inputKey;
      const quote = await this.checkoutService.quote(input).toPromise();
      if (!this.current(version, owner) || revision !== this.quoteVersion || inputKey !== this.inputKey()
          || (selection.fromCart && cartKey !== this.fingerprint())) return;
      this.quote = quote; this.quoteCartKey = cartKey; this.quoteInputKey = inputKey;
      // Use the accepted server prices for the next immediate local estimate.
      for (const item of quote.items) {
        const product = this.catalog.get(item.productId);
        if (product) product.price = item.unitPrice;
        if (selection.product?.id === item.productId) selection.product.price = item.unitPrice;
      }
      this.quoteInputKey = this.inputKey(); this.observedInputKey = this.quoteInputKey;
      this.voucherDiscount = quote.discount || 0; this.failedConfirmation = false;
      this.expiryTimer = setTimeout(() => { this.expiryTimer = undefined;
        if (this.current(version, owner) && !this.busy && !this.uncertainConfirmation && this.quote?.token === quote.token) this.scheduleQuote(0);
      }, Math.max(0, quote.expiresAt - Date.now()) + 20);
    } catch (error) {
      if (this.current(version, owner) && revision === this.quoteVersion) { this.quote = undefined; this.error = this.failure(error); }
    } finally {
      this.quoteTask = undefined;
      const again = this.quoteQueued || (this.current(version, owner) && revision !== this.quoteVersion);
      this.quoteQueued = false;
      if (this.current(version, owner)) {
        this.quoteUpdating = false;
      }
      // A new checkout can start while the prior request is settling. Keep the
      // POSTs serialized, then resume only the current owner's latest inputs.
      if (again && this.checkout && !this.busy && !this.uncertainConfirmation && this.inputValid) this.scheduleQuote(0);
    } };
    this.quoteTask = work();
    return this.quoteTask;
  }

  editQuote(): void {
    if (this.busy || this.failedConfirmation) return;
    this.error = ''; this.scheduleQuote(0);
  }

  /** Checkout commands stay in this component, outside saved conversation history. */
  async handleCheckoutMessage(message: string): Promise<boolean> {
    if (!this.checkout) return false;
    if (this.busy || this.uncertainConfirmation) return true;
    if (!this.authenticated) { this.notice = 'Bạn đăng nhập để tiếp tục lựa chọn này nhé.'; return true; }
    const normalize = (value: string) => value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/\s+/g, ' ').trim();
    const text = normalize(message);
    if (/\?|\b(?:bao nhieu|tai sao|the nao)\b|\bkhong\s*$/.test(text)
        || /^(?:(?:minh|toi|ban)\s+)?(?:khong|chua)\b/.test(text)
        || /\b(?:khong|chua|dung)\s+(?:doi|lay|mua|ap dung|chuyen|xoa|bo|thanh toan|dung|su dung)\b/.test(text)) return false;
    const count = text.match(/(?:doi(?: so luong)?(?: thanh)?|lay|mua|so luong)\s*(\d+)\b/) || text.match(/\b(\d+)\s*(?:cuon|quyen)\b/);
    if (count) {
      const lines = this.checkoutItems;
      const named = lines.filter(item => text.includes(normalize(item.name)));
      const generic = /^(?:(?:doi(?: so luong)?(?: thanh)?|lay|mua|so luong)\s*)?\d+\s*(?:cuon|quyen)?(?:\s*(?:nhe|di|thoi|giup toi|giup minh))*$/.test(text);
      const item = named.length === 1 ? named[0] : lines.length === 1 && generic ? lines[0] : undefined;
      if (!item) { this.notice = 'Bạn ghi thêm tên sách cần đổi số lượng nhé.'; return true; }
      await this.changeCheckoutQuantity(item.id, Number(count[1]));
      if (!this.quantityError && !this.error) this.notice = `Đã đổi “${item.name}” thành ${Number(count[1])} cuốn.`;
      this.changed(); return true;
    }
    if (/^(doi|chuyen)\s+(sang|sach)\s+/.test(text)) {
      const name = text.replace(/^(doi|chuyen)\s+(sang|sach)\s+/, '').replace(/[“”"']/g, '').trim();
      const matches = [...this.catalog.values()].filter(book => normalize(book.name) === name);
      if (this.checkout.fromCart) this.notice = 'Bạn sửa sách trong giỏ hoặc chọn Mua ngay trên cuốn muốn đổi nhé.';
      else if (matches.length !== 1 || matches[0].quantity < 1) this.notice = 'Mình chưa xác định được sách còn bán. Bạn ghi đầy đủ tên sách muốn đổi nhé.';
      else {
        this.checkout.product = matches[0]; this.checkout.quantity = 1; this.quantityError = '';
        this.scheduleQuote(); this.notice = `Đã đổi sang “${matches[0].name}”, số lượng 1 cuốn.`;
      }
      this.changed(); return true;
    }
    if (/(dia chi|giao ve|giao den)/.test(text)) {
      const saved = this.addresses.filter(address =>
        text.includes('mac dinh') ? address.isDefault : !!address.label && text.includes(normalize(address.label)));
      if (saved.length === 1) { this.useAddress(String(saved[0].id)); this.notice = 'Đã chọn địa chỉ bên dưới.'; this.changed(); return true; }
      const street = message.match(/^(?:địa chỉ(?: giao hàng)?|giao (?:về|đến))\s*[:：]\s*(.+)$/i);
      if (street) {
        this.editingAddress = true; this.selectedAddressId = ''; this.form.street = street[1].trim();
        this.deliveryChanged(); this.notice = 'Đã cập nhật địa chỉ cho đơn này.'; this.changed(); return true;
      }
    }
    const recipient = message.match(/^(?:người nhận|tên người nhận)\s*[:：]\s*(.+)$/i);
    const phone = message.match(/^(?:số điện thoại|sđt|sdt)\s*[:：]\s*(.+)$/i);
    if (recipient || phone) {
      this.editingAddress = true; this.selectedAddressId = '';
      if (recipient) this.form.recipient = recipient[1].trim();
      if (phone) this.form.phone = phone[1].replace(/[\s.-]/g, '');
      this.deliveryChanged(); this.notice = 'Đã cập nhật thông tin người nhận bên dưới.'; this.changed(); return true;
    }
    if (/\b(bo|xoa) ma\b/.test(text)) {
      this.voucherCode = ''; this.deliveryChanged(); this.notice = 'Đã bỏ mã giảm giá.'; this.changed(); return true;
    }
    const voucher = text.match(/^(?:dung |ap dung )?(?:ma(?: giam gia)?|voucher)\s*[:：]?\s*([a-z0-9_-]{3,32})$/);
    if (voucher) {
      this.voucherCode = voucher[1].toUpperCase(); this.deliveryChanged();
      this.notice = 'Mình đang kiểm tra mã giảm giá cho lựa chọn hiện tại.'; this.changed(); return true;
    }
    const payment = /\bcod\b|khi nhan hang/.test(text) ? 'COD' : /vnpay/.test(text) ? 'VNPAY'
      : /momo/.test(text) ? 'MOMO' : /\bqr\b|chuyen khoan/.test(text) ? 'BANK_QR' : '';
    if (payment) {
      if (!this.paymentMethods.some(method => method.code === payment)) this.notice = 'Phương thức này hiện chưa dùng được. Bạn chọn một cách thanh toán đang hiển thị nhé.';
      else { this.form.paymentMethod = payment; this.deliveryChanged(); this.notice = `Đã chọn ${this.paymentLabel(payment)}.`; }
      this.changed(); return true;
    }
    return false;
  }

  async confirmOrder(): Promise<void> {
    if (!this.quote || !this.checkout || this.busy || this.placed) return;
    if (!this.failedConfirmation && !this.canConfirmOrder) { this.scheduleQuote(0); return; }
    const quote = this.quote, selection = this.checkout, version = this.version, owner = this.owner();
    let submitted = false;
    if (this.expiryTimer) clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    this.setBusy(true); this.error = ''; this.notice = '';
    try {
      if (!this.failedConfirmation) await this.cart.flush();
      if (!this.current(version, owner)) return;
      if (!this.failedConfirmation && (!this.inputValid || this.quoteInputKey !== this.inputKey()
          || quote.expiresAt <= Date.now() || (selection.fromCart && this.quoteCartKey !== this.fingerprint())))
        throw new Error('Thông tin đã thay đổi. Mình cần tính lại tổng tiền trước khi bạn xác nhận.');
      this.rememberConfirmation(quote.token, quote.paymentMethod, owner);
      submitted = true;
      const result = await this.checkoutService.confirm(quote.token).toPromise();
      if (!this.current(version, owner)) return;
      this.placed = { orderId: result.orderId, paymentMethod: quote.paymentMethod };
      this.forgetConfirmation(owner); this.failedConfirmation = false;
      const saveAddress = this.saveDeliveryAddress && this.editingAddress && !this.addresses.some(address =>
        address.name.trim() === quote.recipient && address.phone.trim() === quote.phone && address.street.trim() === quote.street);
      this.saveDeliveryAddress = false;
      this.clearQuoteWork();
      this.quote = undefined; this.checkout = undefined; this.form = { recipient: '', phone: '', street: '', paymentMethod: 'COD' };
      this.addresses = []; this.selectedAddressId = ''; this.voucherCode = ''; this.voucherDiscount = 0;
      this.notice = `Mình đã tạo đơn #${result.orderId} theo xác nhận của bạn. Bạn có thể mở Đơn hàng để theo dõi nhé.`;
      if (selection.fromCart) { this.cart.refresh(); await this.cart.flush().catch(() => { this.error = 'Đơn đã được tạo, nhưng mình chưa tải lại được giỏ hàng. Bạn tải lại giỏ để xem những sách còn lại nhé.'; }); }
      if (saveAddress && this.current(version, owner)) {
        try { await this.addressesService.addAddress(owner, { label: 'Địa chỉ', name: quote.recipient, phone: quote.phone, street: quote.street }); }
        catch { if (this.current(version, owner)) this.error = 'Đơn đã được tạo, nhưng chưa lưu được địa chỉ vào sổ. Bạn có thể lưu lại trong Tài khoản; không cần đặt lại đơn.'; }
      }
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
    this.clearQuoteWork(); this.undo = undefined; this.quantityError = '';
    const token = this.quote?.token;
    if (token && !this.failedConfirmation) this.checkoutService.cancelDraft(token).subscribe({ error: () => {} });
    this.quote = undefined; this.checkout = undefined; this.placed = undefined; this.pending = undefined;
    this.failedConfirmation = false; this.addresses = []; this.selectedAddressId = ''; this.editingAddress = true;
    this.saveDeliveryAddress = false;
    this.voucherCode='';this.voucherDiscount=0;this.voucherBusy=false;
    this.form = { recipient: '', phone: '', street: '', paymentMethod: 'COD' };
  }
  ngOnDestroy(): void { this.version++; this.discard(); }
}

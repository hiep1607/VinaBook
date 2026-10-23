import {
  AfterViewChecked,
  AfterViewInit,
  Component,
  DoCheck,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatAction, ChatbotService, ChatMessage, ChatProduct } from 'src/app/services/chatbot.service';
import { CartService } from 'src/app/services/cart.service';
import { FabCollisionService } from 'src/app/services/fab-collision.service';
import { AuthService } from 'src/app/services/auth.service';
import { ChatCheckoutService, chatCartFingerprint } from 'src/app/services/chat-checkout.service';
import { ChatCommerceComponent } from '../chat-commerce/chat-commerce.component';
import { WishlistService } from 'src/app/services/wishlist.service';
import { SupportRequestComponent } from '../support-request/support-request.component';
import { chatOrderIds } from 'src/app/shared/order-presentation';

/**
 * Widget trợ lý ảo nổi góc phải dưới. Hỏi đáp về sản phẩm, gợi ý sách và
 * giải đáp chính sách. Backend xử lý hội thoại; ChatCommerceComponent thực thi
 * các thao tác đã xác nhận qua service dùng chung với storefront.
 */
@Component({
  selector: 'app-chatbot',
  templateUrl: './chatbot.component.html',
  styleUrls: ['./chatbot.component.scss']
})
export class ChatbotComponent implements OnInit, OnDestroy, AfterViewChecked, AfterViewInit, DoCheck {

  @ViewChild('scrollBody') private scrollBody!: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') private chatInput!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('fabBtn') private fabBtn?: ElementRef<HTMLButtonElement>;
  @ViewChild(ChatCommerceComponent) commerce?: ChatCommerceComponent;
  @ViewChild(SupportRequestComponent) supportForm?: SupportRequestComponent;

  open = false;
  draft = '';
  typing = false;
  clearing = false;
  historyHasMore = false;
  loadingOlder = false;
  private historyBefore?: number;
  private olderSub?: Subscription;
  messages: ChatMessage[] = [];
  commerceBusy = false;
  wishlistBusy = false;
  cancellationOrderId = 0;
  reorderOrderId = 0;
  supportKind?: 'RETURN' | 'SUPPORT';
  supportOrderId = 0;
  supportSummary = '';
  showSupportRequests = false;
  readonly addedProductIds = new Set<number>();
  orderRefreshKey = 0;
  orderCardIds = chatOrderIds;
  readonly visualOrderMessages = new WeakSet<ChatMessage>();
  errorText = '';
  lastFailedInput = '';

  private shouldScroll = false;
  private scrollTimer?: ReturnType<typeof setTimeout>;
  private viewInitTimer?: ReturnType<typeof setTimeout>;
  private userNearBottom = true;
  private accountEmail?: string;
  reviewProduct: ChatProduct | null = null;
  private get storageKey(): string {
    return 'vinabook_chat_session_v2:' + (this.accountEmail ? 'user:' + encodeURIComponent(this.accountEmail) : 'guest');
  }
  private request?: Subscription;
  private collisionSub?: Subscription;
  private historySub?: Subscription;
  private restoreActionSub?: Subscription;
  private historyVersion = 0;
  private actionSnapshots = new WeakMap<ChatMessage, string>();
  private purchaseLoginActions = new WeakSet<ChatAction>();
  private openedPurchases = new WeakSet<ChatMessage>();

  constructor(
    private chatbot: ChatbotService,
    private cart: CartService,
    private router: Router,
    private collision: FabCollisionService,
    private auth: AuthService,
    private wishlist: WishlistService,
    private checkoutService: ChatCheckoutService
  ) {}

  ngOnInit(): void {
    this.accountEmail = this.auth.currentUser?.email;
    this.chatbot.useAccount(this.accountEmail);
    try { sessionStorage.removeItem('vinabook_chat_session'); } catch {}
    this.messages = this.restoreMessages();
    if (this.messages.length === 0) {
      this.messages.push(this.chatbot.greeting());
    }
    this.loadServerHistory();
  }

  ngOnDestroy(): void {
    if (this.scrollTimer) clearTimeout(this.scrollTimer);
    if (this.viewInitTimer) clearTimeout(this.viewInitTimer);
    this.request?.unsubscribe();
    this.historySub?.unsubscribe();
    this.restoreActionSub?.unsubscribe();
    this.olderSub?.unsubscribe();
    this.collisionSub?.unsubscribe();
  }

  /** Nâng FAB lên trên CTA khi có va chạm ở mobile (xem FabCollisionService). */
  ngAfterViewInit(): void {
    this.collisionSub = this.collision
      .watch(() => this.fabBtn?.nativeElement ?? null)
      .subscribe(lift => {
        const el = this.fabBtn?.nativeElement;
        if (el) {
          el.style.setProperty('--fab-lift', `${lift}px`);
        }
      });
    // Bố cục đầu tiên có thể chưa ổn định — kiểm tra lại sau khi render xong.
    this.viewInitTimer = setTimeout(() => {
      this.collision.recheck();
      this.resumePendingPurchase();
    });
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.shouldScroll = false;
      if (this.scrollTimer) clearTimeout(this.scrollTimer);
      const body = this.scrollBody?.nativeElement;
      this.scrollTimer = setTimeout(() => {
        if (this.open && body === this.scrollBody?.nativeElement) {
          this.scrollToBottom();
          this.userNearBottom = true;
        }
      }, 0);
    }
  }

  ngDoCheck(): void {
    this.syncAccount();
  }

  private syncAccount(): void {
    const email = this.auth.currentUser?.email;
    if (email === this.accountEmail) return;
    this.request?.unsubscribe();
    this.historySub?.unsubscribe();
    this.historyVersion++;
    this.olderSub?.unsubscribe();this.loadingOlder=false;this.historyHasMore=false;this.historyBefore=undefined;
    this.accountEmail = email;
    this.reviewProduct = null;
    this.chatbot.useAccount(email);
    this.messages = this.restoreMessages();
    if (!this.messages.length) this.messages = [this.chatbot.greeting()];
    this.typing = false;
    this.clearing = false;
    this.commerce?.reset();
    this.cancellationOrderId = 0;
    this.reorderOrderId = 0;
    this.supportKind = undefined; this.supportSummary = '';
    this.showSupportRequests = false;
    this.commerceBusy = false; this.wishlistBusy = false;
    this.addedProductIds.clear();
    this.draft = '';
    this.errorText = '';
    this.lastFailedInput = '';
    this.userNearBottom = true;
    this.shouldScroll = true;
    this.loadServerHistory();
    this.resumePendingPurchase();
  }

  private resumePendingPurchase(): void {
    if (!this.commerce || !this.auth.isLoggedIn() || !this.auth.currentUser?.email) return;
    const intent = this.checkoutService.consumePurchaseIntent();
    if (!intent) return;
    this.open = true;
    this.shouldScroll = true;
    const owner = this.accountEmail;
    const version = this.historyVersion;
    void this.commerce.resumePurchase(intent).catch(() => {
      if (owner === this.auth.currentUser?.email && version === this.historyVersion) this.errorText = 'Mình chưa khôi phục được lựa chọn mua. Bạn chọn lại sách để tiếp tục nhé.';
    });
  }

  onScroll(): void {
    const el = this.scrollBody?.nativeElement;
    if (el) this.userNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }

  toggle(): void {
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || this.supportForm?.uncertain) return;
    this.open = !this.open;
    if (!this.open) { this.reviewProduct = null; this.cancellationOrderId = 0; this.reorderOrderId = 0; this.supportKind = undefined; this.supportSummary = ''; this.showSupportRequests = false; }
    if (this.open) {
      this.shouldScroll = true;
      setTimeout(() => { if (this.open) this.chatInput?.nativeElement.focus(); }, 0);
    } else {
      this.chatInput?.nativeElement.blur();
      setTimeout(() => { if (!this.open) this.fabBtn?.nativeElement.focus(); }, 0);
    }
  }

  send(text?: string): void {
    this.syncAccount();
    const value = (text ?? this.draft).trim();
    if (!value || this.typing || this.commerceBusy) {
      return;
    }
    if (this.supportForm?.uncertain || this.supportForm?.review) {
      this.supportForm.error = 'Bạn hoàn tất hoặc sửa yêu cầu đang xem lại trước khi gửi câu hỏi khác nhé, để mình không trộn nội dung cần chuyển cho cửa hàng.';
      return;
    }
    if (this.commerce?.uncertainConfirmation) {
      this.commerce.error = 'Mình cần kiểm tra kết quả xác nhận đơn trước khi thay đổi thông tin. Bạn dùng nút kiểm tra lại trong đơn nhé.';
      return;
    }
    if (this.commerce?.checkout) {
      void this.sendCheckoutMessage(value);
      return;
    }
    this.commerce?.cancelChange();
    this.reviewProduct = null;
    this.cancellationOrderId = 0;
    this.reorderOrderId = 0;
    this.supportKind = undefined; this.supportSummary = '';
    this.showSupportRequests = false;
    if (this.cart.syncing) {
      const email = this.accountEmail;
      this.typing = true;
      this.cart.flush().then(() => {
        this.typing = false;
        if (email === this.accountEmail) this.send(value);
      }, error => {
        this.typing = false;
        this.errorText = error?.message || 'Mình chưa đồng bộ được giỏ hàng nên chưa thể kiểm tra đúng những sách bạn đang chọn. Bạn thử lại sau một chút nhé.';
      });
      return;
    }

    this.messages.push({ from: 'user', text: value });
    this.addedProductIds.clear();
    this.historyVersion++;
    this.draft = '';
    this.userNearBottom = true;
    this.shouldScroll = true;
    this.persistMessages();

    this.typing = true;
    this.errorText = '';
    this.lastFailedInput = '';
    const history = this.messages.slice(0, -1);
    const pageProduct = this.router.url?.match(/^\/product\/(\d+)(?:[/?#]|$)/);
    const activeProductId = pageProduct ? Number(pageProduct[1]) : undefined;
    const cartItems = this.cart.getCartItems().filter(item => Number.isInteger(item.id)
      && Number.isInteger(item.quantity) && item.quantity > 0 && item.quantity <= 99)
      .map(item => ({ productId: item.id, quantity: item.quantity }));
    let responseIndex = -1;
    const cartSnapshot = chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision);
    const wishlistIds = this.accountEmail ? this.wishlist.getIds(this.accountEmail).slice(0, 100) : [];
    this.request = this.chatbot.ask(value, history, activeProductId, cartItems, wishlistIds).subscribe({
      next: message => {
        if (responseIndex < 0) {
          responseIndex = this.messages.length;
          this.messages.push(message);
        } else {
          this.messages[responseIndex] = message;
        }
        this.typing = !!message.streaming;
        this.shouldScroll = this.userNearBottom;
        if (!message.streaming) {
          this.actionSnapshots.set(message, cartSnapshot);
          if (message.execution?.status === 'FAILED') {
            this.lastFailedInput = value;
            this.errorText = 'Mình chưa hoàn tất yêu cầu này. Bạn có thể thử lại bên dưới.';
          }
          this.persistMessages();
          if (message.intent === 'PURCHASE' && message.execution?.tool.split(',').includes('prepareCheckout')
              && !this.openedPurchases.has(message) && !this.commerce?.checkout && !this.commerce?.uncertainConfirmation) {
            const buy = message.actions?.find(action => action.type === 'BUY_NOW' || action.type === 'CHECKOUT_CART');
            if (buy) { this.openedPurchases.add(message); void this.runAction(buy, message); }
          }
        }
      },
      error: error => {
        this.typing = false;
        this.errorText = error?.message || 'Mình đang mất kết nối và chưa trả lời xong. Bạn thử gửi lại câu hỏi nhé.';
        this.lastFailedInput = value;
        this.shouldScroll = true;
      },
      complete: () => {
        this.typing = false;
        this.persistMessages();
        setTimeout(() => { if (this.open) this.chatInput?.nativeElement.focus(); }, 0);
      }
    });
  }

  private async sendCheckoutMessage(value: string): Promise<void> {
    const commerce = this.commerce;
    if (!commerce) return;
    const owner = this.accountEmail;
    const version = this.historyVersion;
    // Checkout text can contain a phone number or address. Keep it out of both
    // the persisted transcript and the general chatbot request/history.
    this.draft = '';
    try {
      const handled = await commerce.handleCheckoutMessage(value);
      if (owner !== this.auth.currentUser?.email || version !== this.historyVersion || commerce !== this.commerce) return;
      if (!handled) commerce.error = 'Bạn có thể đổi số lượng, địa chỉ, phương thức thanh toán hoặc mã giảm giá ngay tại đơn đang chuẩn bị. Nếu muốn hỏi về sách khác, bạn đóng đơn trước nhé.';
      this.shouldScroll = true;
    } catch {
      if (owner === this.auth.currentUser?.email && version === this.historyVersion && commerce === this.commerce) {
        commerce.error = 'Mình chưa cập nhật được đơn. Bạn thử lại hoặc sửa trực tiếp thông tin trong đơn nhé.';
      }
    }
  }

  onInputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clearConversation(): void {
    if (this.clearing || this.commerceBusy || this.commerce?.uncertainConfirmation || this.supportForm?.uncertain) return;
    this.reviewProduct = null;
    this.request?.unsubscribe();
    this.historySub?.unsubscribe();
    this.historyVersion++;
    this.typing = true;
    this.clearing = true;
    this.errorText = '';
    const key = this.storageKey;
    const version = this.historyVersion;
    this.chatbot.newConversation().subscribe({
      next: () => {
        if (version !== this.historyVersion) return;
        this.commerce?.reset();
        this.cancellationOrderId = 0;
        this.reorderOrderId = 0;
        this.supportKind = undefined; this.supportSummary = '';
        this.showSupportRequests = false;
        this.messages = [this.chatbot.greeting()];
        this.historyHasMore = false; this.loadingOlder = false; this.historyBefore = undefined;
        try { localStorage.removeItem(key); } catch {}
        try { sessionStorage.removeItem(key); } catch {}
        this.typing = false;
        this.clearing = false;
        this.userNearBottom = true;
        this.shouldScroll = true;
      },
      error: () => {
        if (version !== this.historyVersion) return;
        this.typing = false;
        this.clearing = false;
        this.errorText = 'Mình chưa xóa được lịch sử cuộc trò chuyện do kết nối chưa ổn định. Bạn thử lại khi có mạng nhé; lịch sử hiện tại vẫn được giữ.';
      }
    });
  }

  openProduct(product: any): void {
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || this.supportForm?.uncertain) return;
    this.router.navigate(['/product', product.id]);
    this.reviewProduct = null;
    this.open = false;
  }

  showCart(): void {
    this.syncAccount();
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || this.supportForm?.uncertain) return;
    this.reviewProduct = null;
    this.cancellationOrderId = 0; this.reorderOrderId = 0;
    this.supportKind = undefined; this.supportSummary = ''; this.showSupportRequests = false;
    void this.commerce?.showCart();
    this.shouldScroll = true;
  }

  actionFor(message: ChatMessage, productId: number, type: ChatAction['type']): ChatAction | undefined {
    return message.actions?.find(action => action.productId === productId && action.type === type);
  }

  async runAction(action: ChatAction, message: ChatMessage, direct = false): Promise<void> {
    if (action.type === 'WRITE_REVIEW') {
      this.syncAccount();
      if (!this.messages.includes(message) || !this.actionAvailable(message) || !Number.isSafeInteger(action.productId)
          || !action.productId || action.productId < 1 || this.commerce?.checkout || this.commerce?.uncertainConfirmation
          || this.supportForm?.uncertain) return;
      const product = message.products?.find(item => item.id === action.productId);
      if (!product) return;
      this.commerce?.dismiss();
      this.cancellationOrderId = 0;
      this.reorderOrderId = 0;
      this.supportKind = undefined;
      this.supportSummary = '';
      this.showSupportRequests = false;
      this.reviewProduct = product;
      this.shouldScroll = true;
      return;
    }
    this.syncAccount();
    if (this.commerceBusy || this.wishlistBusy || this.supportForm?.uncertain) return;
    if (action.type === 'LOGIN' && message.intent === 'PURCHASE' && message.execution?.tool.split(',').includes('prepareCheckout')
        && action.productId != null) {
      const commerce = this.commerce;
      const quantity = action.quantity ?? 1;
      const product = message.products?.find(item => item.id === action.productId);
      if (!commerce || !this.actionAvailable(message) || !message.actions?.includes(action)
          || this.purchaseLoginActions.has(action) || commerce.checkout || commerce.uncertainConfirmation
          || !Number.isSafeInteger(action.productId) || action.productId < 1 || !product
          || !Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, product.quantity)) return;
      this.purchaseLoginActions.add(action);
      this.reviewProduct = null;
      this.cancellationOrderId = 0; this.reorderOrderId = 0;
      this.supportKind = undefined; this.supportSummary = ''; this.showSupportRequests = false;
      const owner = this.auth.currentUser?.email;
      const version = this.historyVersion;
      try {
        await commerce.startCheckout(false, product, quantity);
        if (owner !== this.auth.currentUser?.email || version !== this.historyVersion || commerce !== this.commerce) return;
        if (!this.auth.isLoggedIn()) commerce.login();
        this.shouldScroll = true;
      } catch {
        this.purchaseLoginActions.delete(action);
        if (owner === this.auth.currentUser?.email && version === this.historyVersion) {
          this.errorText = 'Mình chưa giữ được lựa chọn mua. Bạn bấm Đăng nhập để đặt hàng lại nhé.';
        }
      }
      return;
    }
    if (action.type === 'CREATE_RETURN_REQUEST' || action.type === 'CREATE_SUPPORT_REQUEST') {
      if (!this.actionAvailable(message) || (action.type === 'CREATE_RETURN_REQUEST' && !action.orderId)
        || (action.orderId != null && (!Number.isSafeInteger(action.orderId) || action.orderId < 1))) return;
      if (this.commerce?.checkout || this.commerce?.uncertainConfirmation) return;
      this.reviewProduct = null;
      this.commerce?.dismiss();this.cancellationOrderId = 0;this.reorderOrderId = 0;
      this.showSupportRequests = false;
      this.supportKind = action.type === 'CREATE_RETURN_REQUEST' ? 'RETURN' : 'SUPPORT'; this.supportOrderId = action.orderId || 0;
      this.supportSummary = this.supportKind === 'SUPPORT'
        ? this.messages.filter(msg => msg.from === 'user' && !msg.private).slice(-4).map(msg => msg.text).join('\n').slice(0, 1600) : '';
      this.shouldScroll = true; return;
    }
    if (action.type === 'SHOW_SUPPORT_REQUESTS') {
      if (!this.actionAvailable(message) || this.commerce?.checkout || this.commerce?.uncertainConfirmation) return;
      this.reviewProduct = null;
      this.commerce?.dismiss(); this.supportKind = undefined; this.supportSummary = ''; this.cancellationOrderId = 0; this.reorderOrderId = 0;
      this.showSupportRequests = true; this.shouldScroll = true; return;
    }
    if (action.type === 'ADD_TO_WISHLIST' || action.type === 'REMOVE_FROM_WISHLIST') {
      if (!this.actionAvailable(message) || !action.productId || !this.accountEmail) return;
      const product = message.products?.find(item => item.id === action.productId);
      if (!product) return;
      const owner = this.accountEmail; const version = this.historyVersion; this.wishlistBusy = true;
      try {
        if (action.type === 'ADD_TO_WISHLIST') await this.wishlist.addToWishlist(this.accountEmail, product.id);
        else await this.wishlist.removeFromWishlist(this.accountEmail, product.id);
        if (owner !== this.auth.currentUser?.email || version !== this.historyVersion) return;
        this.messages.push({ from: 'bot', text: action.type === 'ADD_TO_WISHLIST'
          ? `Mình đã lưu “${product.name}” vào yêu thích trong tài khoản của bạn. Bạn có thể xem lại ở mục Yêu thích trên web nhé.`
          : `Mình đã bỏ “${product.name}” khỏi danh sách yêu thích trong tài khoản của bạn.`, intent: 'PRODUCT_INFO' });
        this.persistMessages(); this.shouldScroll = true;
      } catch (error) { if(owner === this.auth.currentUser?.email && version === this.historyVersion) this.errorText = (error as any)?.error?.message || 'Mình chưa lưu được thay đổi trên server. Bạn thử lại nhé.'; }
      finally { if(version === this.historyVersion) this.wishlistBusy = false; }
      return;
    }
    if (action.type === 'CANCEL_ORDER' || action.type === 'REORDER_ORDER') {
      if (!this.actionAvailable(message) || !Number.isSafeInteger(action.orderId) || !action.orderId || action.orderId < 1) return;
      if (this.commerce?.checkout || this.commerce?.uncertainConfirmation) {
        this.commerce.error = 'Bạn hoàn tất hoặc đóng bước đặt hàng trước khi mở thao tác với đơn cũ nhé, để mình giữ rõ từng yêu cầu.';
        return;
      }
      this.commerce?.dismiss();
      this.reviewProduct = null;
      this.supportKind = undefined; this.supportSummary = '';
      this.showSupportRequests = false;
      this.cancellationOrderId = action.type === 'CANCEL_ORDER' ? action.orderId : 0;
      this.reorderOrderId = action.type === 'REORDER_ORDER' ? action.orderId : 0;
      this.shouldScroll = true;
      return;
    }
    if (['ADD_TO_CART', 'REMOVE_FROM_CART', 'UPDATE_CART_QUANTITY', 'CLEAR_CART', 'SHOW_CART', 'BUY_NOW', 'CHECKOUT_CART'].includes(action.type)) {
      if (!this.actionAvailable(message)) return;
      this.reviewProduct = null;
      this.cancellationOrderId = 0; this.reorderOrderId = 0;
      this.supportKind = undefined; this.supportSummary = '';
      this.showSupportRequests = false;
      const product = message.products?.find(item => item.id === action.productId);
      if (direct && action.type === 'ADD_TO_CART') {
        if (!product || !message.actions?.includes(action) || this.addedProductIds.has(product.id)) return;
        // This is a fresh click on the current book card. Bind only this
        // reversible add to the cart as it is now, including after Undo. Keep
        // the reply's original snapshot for every other proposed cart action.
        const currentCart = chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision);
        this.commerce?.runAction(action, product, currentCart, true);
      } else {
        this.commerce?.runAction(action, product, this.actionSnapshots.get(message));
      }
      this.shouldScroll = true;
      return;
    }
    if (action.route) {
      this.reviewProduct = null;
      this.router.navigateByUrl(action.route);
      this.open = false;
    }
  }

  actionAvailable(message: ChatMessage): boolean {
    return !this.typing && !this.commerceBusy && !this.wishlistBusy && this.actionSnapshots.has(message)
      && this.messages[this.messages.length - 1] === message;
  }

  trackProductAction(_index: number, action: ChatAction): string {
    return action.type;
  }

  compactProducts(message: ChatMessage): boolean {
    if (message.intent !== 'PRODUCT_INFO' || !message.products?.length || message.execution?.tool !== 'getProducts') return false;
    const index = this.messages.indexOf(message);
    const earlier = this.messages.slice(0, index).filter(item => item.from === 'bot');
    return message.products.every(product => earlier.some(item => item.products?.some(book => book.id === product.id)));
  }

  contextualChips(message: ChatMessage): string[] {
    if (this.commerce?.checkout || this.commerce?.uncertainConfirmation) return [];
    if (message.intent === 'PRODUCT_INFO' && message.products?.length === 1) {
      return message.text.includes('Ví dụ minh họa') ? ['Mua 1 cuốn này', 'Tìm sách tương tự'] : ['Cho ví dụ áp dụng ý vừa nói', 'Mua 1 cuốn này'];
    }
    return message.chips || [];
  }

  productActions(message: ChatMessage, productId: number): ChatAction[] {
    if (!this.actionAvailable(message)) return [];
    if (message.intent === 'PURCHASE' && this.commerce?.checkout) return [];
    const actions = (message.actions || []).filter(action => action.productId === productId && action.type !== 'VIEW_PRODUCT');
    const product = message.products?.find(item => item.id === productId);
    const add = actions.find(action => action.type === 'ADD_TO_CART');
    const quantity = add?.quantity ?? 1;
    if (!add || actions.some(action => action.type === 'BUY_NOW') || !product
        || !Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(99, product.quantity)) return actions;
    const buy: ChatAction = { type: 'BUY_NOW', label: 'Mua ngay', productId, quantity, requiresConfirmation: false };
    const index = actions.indexOf(add);
    return [...actions.slice(0, index + 1), buy, ...actions.slice(index + 1)];
  }

  commerceUpdated(): void { this.shouldScroll = true; }

  orderCancelled(id: number): void {
    this.orderRefreshKey++;
    this.messages.push({ from: 'bot', text: `Đơn #${id} đã được hủy. Bạn có thể xem trạng thái mới trong mục Đơn hàng nhé.`, intent: 'ORDER_SUPPORT', private: true });
    this.shouldScroll = true;
  }

  sendFeedback(message: ChatMessage, value: 'positive' | 'negative'): void {
    if (!message.messageId || message.feedback) return;
    this.chatbot.feedback(message.messageId, value).subscribe({
      next: () => {
        message.feedback = value;
        this.persistMessages();
      }
    });
  }

  retry(): void {
    const value = this.lastFailedInput;
    this.errorText = '';
    this.lastFailedInput = '';
    if (value) this.send(value);
  }

  genericActions(message: ChatMessage): ChatAction[] {
    return this.actionAvailable(message) && !(message.intent === 'PURCHASE' && this.commerce?.checkout)
      ? (message.actions || []).filter(action => !action.productId) : [];
  }

  get showTypingDots(): boolean {
    const last = this.messages[this.messages.length - 1];
    return this.typing && !last?.streaming;
  }

  trackByIndex(index: number): number {
    return index;
  }

  answerText(message: ChatMessage): string {
    if (message.intent !== 'DISCOVER_BOOKS' || !message.products?.length) return message.text;
    const names = new Set(message.products.map(book => book.name));
    return message.text.split('\n').map(line => {
      const title=line.match(/^•\s*“([^”]+)”\s*—/);
      return title && names.has(title[1]) ? `• “${title[1]}”` : line;
    }).join('\n').replace(/^\*\*(.+)\*\*$/gm, '$1');
  }

  private restoreMessages(): ChatMessage[] {
    try {
      const parsed = JSON.parse(localStorage.getItem(this.storageKey) || sessionStorage.getItem(this.storageKey) || '[]');
      return Array.isArray(parsed) ? parsed.filter(message => !message.private && message.intent !== 'ORDER_SUPPORT').slice(-24) : [];
    } catch {
      return [];
    }
  }

  private persistMessages(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.messages.filter(message => !message.private && message.intent !== 'ORDER_SUPPORT').slice(-24)));
    } catch {
      // Trình duyệt chặn storage: hội thoại vẫn hoạt động trong bộ nhớ.
    }
  }

  private loadServerHistory(): void {
    const version = this.historyVersion;
    this.historySub?.unsubscribe();
    this.historySub = this.chatbot.history().subscribe({
      next: messages => {
        if (version !== this.historyVersion || this.typing || !messages.length) return;
        this.messages = messages;
        this.historyBefore = messages[0]?.historyId;
        this.historyHasMore = messages.length >= 50 && !!this.historyBefore;
        this.restoreLatestActions(version);
        this.persistMessages();
        this.shouldScroll = true;
      },
      error: () => { /* The small local cache remains available while offline. */ }
    });
  }

  loadOlderMessages(): void {
    if (!this.historyBefore || this.loadingOlder) return;
    const version = this.historyVersion;this.loadingOlder = true;
    this.olderSub = this.chatbot.historyPage(this.historyBefore).subscribe({ next: page => {
      if (version !== this.historyVersion) return;
      this.messages = [...page.messages, ...this.messages];
      this.historyBefore = page.nextBefore;this.historyHasMore = page.hasMore;this.loadingOlder = false;
    }, error: () => { if(version===this.historyVersion){this.loadingOlder=false;this.errorText='Chưa tải được tin nhắn cũ. Bạn thử lại nhé.';} } });
  }

  private restoreLatestActions(version: number): void {
    this.restoreActionSub?.unsubscribe();
    const message = this.messages[this.messages.length - 1];
    if (!message || message.from !== 'bot' || !message.actions?.length) return;
    this.restoreActionSub = this.chatbot.restoreActions(message).subscribe({ next: restored => {
      if (version !== this.historyVersion || this.typing || this.messages[this.messages.length - 1] !== message) return;
      this.messages[this.messages.length - 1] = restored;
      this.actionSnapshots.set(restored, chatCartFingerprint(this.cart.getCartItems(), this.cart.serverRevision));
      this.persistMessages();
    }, error: () => { /* Keep history readable; retry the action after connectivity recovers. */ } });
  }

  private scrollToBottom(): void {
    try {
      const el = this.scrollBody.nativeElement;
      el.scrollTop = el.scrollHeight;
    } catch {
      // view chưa sẵn sàng — bỏ qua
    }
  }
}

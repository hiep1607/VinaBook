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
import { chatCartFingerprint } from 'src/app/services/chat-checkout.service';
import { ChatCommerceComponent } from '../chat-commerce/chat-commerce.component';
import { WishlistService } from 'src/app/services/wishlist.service';

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

  open = false;
  draft = '';
  typing = false;
  clearing = false;
  messages: ChatMessage[] = [];
  commerceBusy = false;
  cancellationOrderId = 0;
  reorderOrderId = 0;
  supportKind?: 'RETURN' | 'SUPPORT';
  supportOrderId = 0;
  supportSummary = '';
  showSupportRequests = false;
  readonly addedProductIds = new Set<number>();
  errorText = '';
  lastFailedInput = '';

  private shouldScroll = false;
  private scrollTimer?: ReturnType<typeof setTimeout>;
  private userNearBottom = true;
  private accountEmail?: string;
  reviewProduct: ChatProduct | null = null;
  private get storageKey(): string {
    return 'vinabook_chat_session_v2:' + (this.accountEmail ? 'user:' + encodeURIComponent(this.accountEmail) : 'guest');
  }
  private request?: Subscription;
  private collisionSub?: Subscription;
  private historySub?: Subscription;
  private historyVersion = 0;
  private actionSnapshots = new WeakMap<ChatMessage, string>();

  constructor(
    private chatbot: ChatbotService,
    private cart: CartService,
    private router: Router,
    private collision: FabCollisionService,
    private auth: AuthService,
    private wishlist: WishlistService
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
    this.request?.unsubscribe();
    this.historySub?.unsubscribe();
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
    setTimeout(() => this.collision.recheck());
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
    this.commerceBusy = false;
    this.addedProductIds.clear();
    this.errorText = '';
    this.lastFailedInput = '';
    this.userNearBottom = true;
    this.shouldScroll = true;
    this.loadServerHistory();
  }

  onScroll(): void {
    const el = this.scrollBody?.nativeElement;
    if (el) this.userNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }

  toggle(): void {
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || false) return;
    this.open = !this.open;
    if (!this.open) { this.reviewProduct = null; this.cancellationOrderId = 0; this.reorderOrderId = 0; this.supportKind = undefined; this.supportSummary = ''; this.showSupportRequests = false; }
    if (this.open) {
      this.shouldScroll = true;
      setTimeout(() => this.chatInput?.nativeElement.focus(), 0);
    }
  }

  send(text?: string): void {
    this.syncAccount();
    const value = (text ?? this.draft).trim();
    if (!value || this.typing || this.commerceBusy) {
      return;
    }

    if (this.commerce?.checkout || this.commerce?.uncertainConfirmation) {
      this.commerce.error = 'Bạn hoàn tất hoặc đóng bước đặt hàng trước khi gửi câu hỏi mới nhé, để mình không trộn yêu cầu mới với đơn đang chuẩn bị.';
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
          this.persistMessages();
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
        setTimeout(() => this.chatInput?.nativeElement.focus(), 0);
      }
    });
  }

  onInputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clearConversation(): void {
    if (this.clearing || this.commerceBusy || this.commerce?.uncertainConfirmation || false) return;
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
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || false) return;
    this.router.navigate(['/product', product.id]);
    this.reviewProduct = null;
    this.open = false;
  }

  showCart(): void {
    this.syncAccount();
    if (this.commerceBusy || this.commerce?.uncertainConfirmation || false) return;
    this.reviewProduct = null;
    this.cancellationOrderId = 0; this.reorderOrderId = 0;
    this.supportKind = undefined; this.supportSummary = ''; this.showSupportRequests = false;
    void this.commerce?.showCart();
    this.shouldScroll = true;
  }

  actionFor(message: ChatMessage, productId: number, type: ChatAction['type']): ChatAction | undefined {
    return message.actions?.find(action => action.productId === productId && action.type === type);
  }

  runAction(action: ChatAction, message: ChatMessage): void {

    this.syncAccount();
    if (this.commerceBusy || false) return;


    if (action.type === 'ADD_TO_WISHLIST' || action.type === 'REMOVE_FROM_WISHLIST') {
      if (!this.actionAvailable(message) || !action.productId || !this.accountEmail) return;
      const product = message.products?.find(item => item.id === action.productId);
      if (!product) return;
      try {
        if (action.type === 'ADD_TO_WISHLIST') this.wishlist.addToWishlist(this.accountEmail, product.id);
        else this.wishlist.removeFromWishlist(this.accountEmail, product.id);
        this.messages.push({ from: 'bot', text: action.type === 'ADD_TO_WISHLIST'
          ? `Mình đã lưu “${product.name}” vào yêu thích trên thiết bị này. Bạn có thể xem lại ở mục Yêu thích trên web nhé.`
          : `Mình đã bỏ “${product.name}” khỏi danh sách yêu thích trên thiết bị này.`, intent: 'PRODUCT_INFO' });
        this.persistMessages(); this.shouldScroll = true;
      } catch { this.errorText = 'Trình duyệt chưa lưu được thay đổi. Bạn kiểm tra quyền lưu dữ liệu rồi thử lại nhé.'; }
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
      this.commerce?.runAction(action, product, this.actionSnapshots.get(message));
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
    return !this.typing && !this.commerceBusy && this.actionSnapshots.has(message)
      && this.messages[this.messages.length - 1] === message;
  }

  productActions(message: ChatMessage, productId: number): ChatAction[] {
    return this.actionAvailable(message) ? (message.actions || []).filter(action => action.productId === productId && action.type !== 'VIEW_PRODUCT') : [];
  }

  commerceUpdated(): void { this.shouldScroll = true; }

  orderCancelled(id: number): void {
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
    return this.actionAvailable(message) ? (message.actions || []).filter(action => !action.productId) : [];
  }

  get showTypingDots(): boolean {
    const last = this.messages[this.messages.length - 1];
    return this.typing && !last?.streaming;
  }

  trackByIndex(index: number): number {
    return index;
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
        this.persistMessages();
        this.shouldScroll = true;
      },
      error: () => { /* The small local cache remains available while offline. */ }
    });
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

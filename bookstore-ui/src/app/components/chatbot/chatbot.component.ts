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

/**
 * Widget trợ lý ảo nổi góc phải dưới. Hỏi đáp về sản phẩm, gợi ý sách và
 * giải đáp hình thức thanh toán. Logic ở ChatbotService (rule-based trên
 * catalog cache). Render ở app.component.html, hiển thị trên mọi trang.
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

  open = false;
  draft = '';
  typing = false;
  clearing = false;
  messages: ChatMessage[] = [];
  pendingAction?: { action: ChatAction; message: ChatMessage; product: ChatProduct };
  readonly addedProductIds = new Set<number>();
  errorText = '';
  lastFailedInput = '';

  private shouldScroll = false;
  private userNearBottom = true;
  private accountEmail?: string;
  private get storageKey(): string {
    return 'vinabook_chat_session_v2:' + (this.accountEmail ? 'user:' + encodeURIComponent(this.accountEmail) : 'guest');
  }
  private request?: Subscription;
  private collisionSub?: Subscription;
  private historySub?: Subscription;
  private historyVersion = 0;

  constructor(
    private chatbot: ChatbotService,
    private cart: CartService,
    private router: Router,
    private collision: FabCollisionService,
    private auth: AuthService
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
      this.scrollToBottom();
      this.shouldScroll = false;
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
    this.chatbot.useAccount(email);
    this.messages = this.restoreMessages();
    if (!this.messages.length) this.messages = [this.chatbot.greeting()];
    this.typing = false;
    this.clearing = false;
    this.pendingAction = undefined;
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
    this.open = !this.open;
    if (this.open) {
      this.shouldScroll = true;
      setTimeout(() => this.chatInput?.nativeElement.focus(), 0);
    }
  }

  send(text?: string): void {
    this.syncAccount();
    const value = (text ?? this.draft).trim();
    if (!value || this.typing) {
      return;
    }
    if (this.cart.syncing) {
      const email = this.accountEmail;
      this.typing = true;
      this.cart.flush().then(() => {
        this.typing = false;
        if (email === this.accountEmail) this.send(value);
      }, error => {
        this.typing = false;
        this.errorText = error?.message || 'Giỏ hàng chưa đồng bộ. Vui lòng thử lại.';
      });
      return;
    }

    this.messages.push({ from: 'user', text: value });
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
    this.request = this.chatbot.ask(value, history, activeProductId, cartItems).subscribe({
      next: message => {
        if (responseIndex < 0) {
          responseIndex = this.messages.length;
          this.messages.push(message);
        } else {
          this.messages[responseIndex] = message;
        }
        this.typing = !!message.streaming;
        this.shouldScroll = this.userNearBottom;
        if (!message.streaming) this.persistMessages();
      },
      error: error => {
        this.typing = false;
        this.errorText = error?.message || 'Trợ lý đang mất kết nối. Bạn có thể thử gửi lại câu hỏi.';
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
    if (this.clearing) return;
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
        this.pendingAction = undefined;
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
        this.errorText = 'Chưa xóa được lịch sử trên máy chủ. Hãy thử lại khi có kết nối.';
      }
    });
  }

  openProduct(product: any): void {
    this.router.navigate(['/product', product.id]);
    this.open = false;
  }

  actionFor(message: ChatMessage, productId: number, type: ChatAction['type']): ChatAction | undefined {
    return message.actions?.find(action => action.productId === productId && action.type === type);
  }

  runAction(action: ChatAction, message: ChatMessage): void {
    if (action.type === 'ADD_TO_CART' && action.productId) {
      const product = message.products?.find(item => item.id === action.productId);
      if (!product) return;
      if (action.requiresConfirmation) {
        this.pendingAction = { action, message, product };
        this.shouldScroll = true;
      } else {
        this.addToCart(product, action.quantity || 1);
      }
      return;
    }
    if (action.route) {
      this.router.navigateByUrl(action.route);
      this.open = false;
    }
  }

  confirmAddToCart(): void {
    if (!this.pendingAction) return;
    this.addToCart(this.pendingAction.product, this.pendingAction.action.quantity || 1);
    this.pendingAction = undefined;
  }

  cancelAction(): void {
    this.pendingAction = undefined;
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
    return (message.actions || []).filter(action => !action.productId);
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
      return Array.isArray(parsed) ? parsed.slice(-24) : [];
    } catch {
      return [];
    }
  }

  private addToCart(product: ChatProduct, quantity = 1): void {
    this.cart.addToCart(product, quantity);
    this.addedProductIds.add(product.id);
    this.shouldScroll = true;
  }

  private persistMessages(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.messages.slice(-24)));
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

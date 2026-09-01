import {
  AfterViewChecked,
  AfterViewInit,
  Component,
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
export class ChatbotComponent implements OnInit, OnDestroy, AfterViewChecked, AfterViewInit {

  @ViewChild('scrollBody') private scrollBody!: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') private chatInput!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('fabBtn') private fabBtn?: ElementRef<HTMLButtonElement>;

  open = false;
  draft = '';
  typing = false;
  messages: ChatMessage[] = [];
  pendingAction?: { action: ChatAction; message: ChatMessage; product: ChatProduct };
  readonly addedProductIds = new Set<number>();
  errorText = '';
  lastFailedInput = '';

  private shouldScroll = false;
  private readonly storageKey = 'vinabook_chat_session';
  private request?: Subscription;
  private collisionSub?: Subscription;

  constructor(
    private chatbot: ChatbotService,
    private cart: CartService,
    private router: Router,
    private collision: FabCollisionService
  ) {}

  ngOnInit(): void {
    this.messages = this.restoreMessages();
    if (this.messages.length === 0) {
      this.messages.push(this.chatbot.greeting());
    }
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
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

  toggle(): void {
    this.open = !this.open;
    if (this.open) {
      this.shouldScroll = true;
      setTimeout(() => this.chatInput?.nativeElement.focus(), 0);
    }
  }

  send(text?: string): void {
    const value = (text ?? this.draft).trim();
    if (!value || this.typing) {
      return;
    }

    this.messages.push({ from: 'user', text: value });
    this.draft = '';
    this.shouldScroll = true;
    this.persistMessages();

    this.typing = true;
    this.errorText = '';
    this.lastFailedInput = '';
    const history = this.messages.slice(0, -1);
    let responseIndex = -1;
    this.request = this.chatbot.ask(value, history).subscribe({
      next: message => {
        if (responseIndex < 0) {
          responseIndex = this.messages.length;
          this.messages.push(message);
        } else {
          this.messages[responseIndex] = message;
        }
        this.typing = !!message.streaming;
        this.shouldScroll = true;
        if (!message.streaming) this.persistMessages();
      },
      error: () => {
        this.typing = false;
        this.errorText = 'Trợ lý đang mất kết nối. Bạn có thể thử gửi lại câu hỏi.';
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
    this.request?.unsubscribe();
    this.typing = false;
    this.pendingAction = undefined;
    this.errorText = '';
    this.messages = [this.chatbot.greeting()];
    sessionStorage.removeItem(this.storageKey);
    this.shouldScroll = true;
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
        this.addToCart(product);
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
    this.addToCart(this.pendingAction.product);
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
      const parsed = JSON.parse(sessionStorage.getItem(this.storageKey) || '[]');
      return Array.isArray(parsed) ? parsed.slice(-24) : [];
    } catch {
      return [];
    }
  }

  private addToCart(product: ChatProduct): void {
    this.cart.addToCart(product);
    this.addedProductIds.add(product.id);
    this.shouldScroll = true;
  }

  private persistMessages(): void {
    try {
      sessionStorage.setItem(this.storageKey, JSON.stringify(this.messages.slice(-24)));
    } catch {
      // Trình duyệt chặn storage: hội thoại vẫn hoạt động trong bộ nhớ.
    }
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

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
import { ChatbotService, ChatMessage } from 'src/app/services/chatbot.service';
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

  private shouldScroll = false;
  private readonly storageKey = 'vinabook_chat_session';
  private request?: Subscription;
  private collisionSub?: Subscription;

  constructor(
    private chatbot: ChatbotService,
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
    const history = this.messages.slice(0, -1);
    this.request = this.chatbot.ask(value, history).subscribe(message => {
      this.messages.push(message);
      this.typing = false;
      this.shouldScroll = true;
      this.persistMessages();
      setTimeout(() => this.chatInput?.nativeElement.focus(), 0);
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
    this.messages = [this.chatbot.greeting()];
    sessionStorage.removeItem(this.storageKey);
    this.shouldScroll = true;
  }

  openProduct(product: any): void {
    this.router.navigate(['/product', product.id]);
    this.open = false;
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

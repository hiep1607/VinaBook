import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subscriber, Subscription } from 'rxjs';
import { map, tap, timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

export type ChatIntent =
  | 'DISCOVER_BOOKS' | 'PRODUCT_INFO' | 'COMPARE_BOOKS'
  | 'POLICY' | 'ORDER_SUPPORT' | 'SMALL_TALK' | 'UNKNOWN' | 'PURCHASE';

export interface ChatAction {
  type: 'VIEW_ORDER' | 'VIEW_NOTIFICATIONS' | 'VIEW_CART' | 'VIEW_PRODUCT' | 'ADD_TO_CART' | 'REMOVE_FROM_CART' | 'UPDATE_CART_QUANTITY' | 'CLEAR_CART' | 'CANCEL_ORDER' | 'SHOW_CART' | 'LOGIN' | 'VIEW_ORDERS' | 'BUY_NOW' | 'CHECKOUT_CART' | 'ADD_TO_WISHLIST' | 'REMOVE_FROM_WISHLIST' | 'REORDER_ORDER' | 'CREATE_RETURN_REQUEST' | 'CREATE_SUPPORT_REQUEST' | 'SHOW_SUPPORT_REQUESTS' | 'WRITE_REVIEW';
  orderId?: number;
  label: string;
  productId?: number;
  route?: string;
  requiresConfirmation: boolean;
  quantity?: number;
}

export interface ChatProduct {
  id: number;
  name: string;
  author: string;
  category: string;
  price: number;
  oldPrice: number;
  discount: number;
  quantity: number;
  image: string;
}

export interface ChatMessage {
  execution?: { tool: string; status: string };
  from: 'bot' | 'user';
  text: string;
  products?: ChatProduct[];
  chips?: string[];
  actions?: ChatAction[];
  source?: 'ai' | 'local';
  provider?: string;
  fallback?: boolean;
  messageId?: string;
  intent?: ChatIntent;
  streaming?: boolean;
  progress?: string;
  historyId?: number;
  feedback?: 'positive' | 'negative';
  private?: boolean;
}

interface ChatApiResponse {
  execution?: { tool: string; status: string };
  answer: string;
  provider: string;
  model: string;
  fallback: boolean;
  products: ChatProduct[];
  chips: string[];
  actions: ChatAction[];
  messageId: string;
  intent: ChatIntent;
}

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly api = environment.apiUrl + '/api/chat';
  private sessionKey = 'vinabook_chat_id_v3:guest';
  private readonly ephemeralSessionIds = new Map<string, string>();

  useAccount(email?: string): void {
    this.sessionKey = 'vinabook_chat_id_v3:' + (email ? 'user:' + encodeURIComponent(email) : 'guest');
  }

  constructor(private http: HttpClient) {}

  greeting(): ChatMessage {
    return {
      from: 'bot',
      text: 'Chào bạn! Mình là trợ lý sách của VinaBook, có thể cùng bạn tìm một cuốn để đọc, chọn quà hoặc so sánh những cuốn đang cân nhắc. Bạn muốn đọc về chủ đề gì hoặc để làm gì? Biết hướng đọc sẽ giúp mình gợi ý sát hơn. Nếu có người nhận cụ thể, mức đọc hoặc ngân sách dự kiến, bạn có thể nói thêm để mình chọn kỹ hơn.',
      chips: ['Gợi ý sách dưới 150k', 'Tìm sách làm quà', 'So sánh hai cuốn', 'Đơn hàng của tôi'],
      source: 'local',
      intent: 'SMALL_TALK'
    };
  }

  ask(input: string, history: ChatMessage[], activeProductId?: number, cartItems: { productId: number; quantity: number }[] = [], wishlistProductIds: number[] = []): Observable<ChatMessage> {
    return new Observable<ChatMessage>(observer => {
      const controller = new AbortController();
      let fallbackSub: Subscription | undefined;
      let streamTimedOut = false;
      const deadlineAt = Date.now() + 60000;
      const deadline = setTimeout(() => {
        streamTimedOut = true;
        controller.abort();
        observer.error(this.transportError(504));
      }, 60000);
      const body = {
        message: input,
        requestId: 'turn-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2),
        activeProductId,
        cartItems,
        wishlistProductIds,
        sessionId: this.sessionId(),
		contextProductIds: this.contextProductIds(history),
        history: this.prepareHistory(history)
      };

      const run = async () => {
        try {
          const response = await fetch(this.api + '/stream', {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify(body),
            signal: controller.signal
          });
          if ([400, 401, 403, 413, 429].includes(response.status)) {
            observer.error(this.transportError(response.status));
            return;
          }
          if (!response.ok || !response.body) {
            throw new Error('stream-unavailable');
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder('utf-8');
          let buffer = '';
          while (true) {
            const result = await reader.read();
            if (result.done) break;
            buffer += decoder.decode(result.value, { stream: true });
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() || '';
            for (const frame of frames) {
              const event = this.parseEvent(frame);
              if (!event) continue;
              if (event.name === 'done') {
                clearTimeout(deadline);
                // The answer has been grounded and checked by the backend. Reveal
                // this completed response locally so unverified text never flashes.
                await this.reveal(event.data as ChatApiResponse, observer, controller.signal);
                observer.complete();
                return;
              } else if (event.name === 'progress' || event.name === 'ready') {
                const labels: Record<string,string> = { understanding: 'Mình đang đọc yêu cầu của bạn…', execution: 'Mình đang kiểm tra sách và thông tin liên quan…' };
                observer.next({ from: 'bot', text: '', streaming: true, progress: labels[event.data.stage] || 'Mình đang kiểm tra yêu cầu…' });
              } else if (event.name === 'error') {
                observer.error(new Error(event.data.message || 'Mình chưa hoàn tất câu trả lời. Bạn thử gửi lại câu hỏi nhé để mình kiểm tra tiếp.'));
                return;
              }
            }
          }
          throw new Error('stream-ended');
        } catch (error) {
          if (streamTimedOut || controller.signal.aborted || observer.closed) return;
          fallbackSub = this.http.post<ChatApiResponse>(this.api, body).pipe(timeout(Math.max(1, deadlineAt - Date.now()))).subscribe({
            next: response => {
              clearTimeout(deadline);
              this.reveal(response, observer, controller.signal).then(() => observer.complete());
            },
            error: error => observer.error(this.transportError(error.name === 'TimeoutError' ? 504 : error.status))
          });
        }
      };

      run();
      return () => {
        clearTimeout(deadline);
        controller.abort();
        fallbackSub?.unsubscribe();
      };
    });
  }

  feedback(messageId: string, value: 'positive' | 'negative'): Observable<void> {
    return this.http.post<void>(this.api + '/feedback', { messageId, value });
  }

  history(): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(this.api + '/conversation', { params: { sessionId: this.sessionId() } });
  }

  historyPage(before: number): Observable<{messages: ChatMessage[]; nextBefore?: number; hasMore: boolean}> {
    return this.http.get<{messages: ChatMessage[]; nextBefore?: number; hasMore: boolean}>(this.api + '/conversation/page',
      {params: {sessionId: this.sessionId(), before: String(before), limit: '25'}});
  }

  newConversation(): Observable<void> {
    const oldId = this.sessionId();
    const key = this.sessionKey;
    return this.http.delete<void>(this.api + '/conversation', { params: { sessionId: oldId } }).pipe(
      tap(() => {
        this.ephemeralSessionIds.delete(key);
        try { localStorage.removeItem(key); } catch {}
        try { sessionStorage.removeItem(key); } catch {}
      })
    );
  }

  recordProductView(productId: number): void {
    if (!Number.isInteger(productId) || productId <= 0) return;
    this.http.post<void>(this.api + '/activity', { productId }).subscribe({ error: () => {} });
  }

  private async reveal(response: ChatApiResponse, observer: Subscriber<ChatMessage>, signal: AbortSignal): Promise<void> {
    if (!signal.aborted && !observer.closed) observer.next(this.fromResponse(response));
  }

  restoreActions(message: ChatMessage): Observable<ChatMessage> {
    const safe = new Set(['LOGIN','BUY_NOW','ADD_TO_CART','CHECKOUT_CART','SHOW_CART','VIEW_PRODUCT','VIEW_CART','VIEW_ORDERS','VIEW_ORDER','VIEW_NOTIFICATIONS']);
    return this.http.get<any[]>(environment.apiUrl + '/api/products').pipe(timeout(10000), map(catalog => {
      const products = (message.products || []).map(old => {
        const fresh = catalog.find(book => book.productId === old.id);
        return fresh ? { ...old, name: fresh.name, author: fresh.author, quantity: fresh.quantity,
          oldPrice: fresh.price, discount: fresh.discount, price: Math.round(fresh.price * (100 - fresh.discount) / 100) } : { ...old, quantity: 0 };
      });
      return { ...message, products, actions: (message.actions || []).filter(action => safe.has(action.type)
        && (!action.productId || products.some(book => book.id === action.productId
          && (action.type === 'VIEW_PRODUCT' || book.quantity >= Math.max(1, action.quantity || 1))))) };
    }));
  }

  private prepareHistory(history: ChatMessage[]): {role: string; content: string}[] {
    let remaining = 24000;
    const result: {role: string; content: string}[] = [];
    const recent = history.filter(m => m.text?.trim() && !m.streaming && !m.private && m.intent !== 'ORDER_SUPPORT').slice(-8);
    for (let i = recent.length - 1; i >= 0 && remaining > 0; i--) {
      let content = recent[i].text.slice(0, Math.min(8000, remaining));
      // Do not split a UTF-16 surrogate pair at the budget boundary.
      if (/[\uD800-\uDBFF]$/.test(content)) content = content.slice(0, -1);
      if (!content.trim()) continue;
      result.unshift({role: recent[i].from === 'user' ? 'user' : 'assistant', content});
      remaining -= content.length;
    }
    return result;
  }

  private transportError(status: number): Error {
    const messages: {[key: number]: string} = {
      400: 'Mình chưa đọc được câu hỏi hoặc lịch sử chat này. Bạn thử rút gọn câu hỏi hoặc bắt đầu cuộc trò chuyện mới để mình nhận đủ nội dung nhé.',
      413: 'Lịch sử chat đã quá dài để gửi đầy đủ. Bạn bắt đầu cuộc trò chuyện mới rồi nói lại nhu cầu hiện tại nhé.',
      401: 'Phiên đăng nhập đã hết hạn nên mình chưa thể xử lý yêu cầu cho tài khoản của bạn. Bạn đăng nhập lại rồi gửi câu hỏi nhé.',
      403: 'Mình chưa thể thực hiện yêu cầu trong phiên hiện tại. Bạn kiểm tra tài khoản đang đăng nhập rồi thử lại nhé.',
      429: 'Mình đang nhận nhiều câu hỏi liên tiếp. Bạn chờ khoảng một phút rồi gửi lại nhé, để mình xử lý đầy đủ từng yêu cầu.',
      504: 'Mình mất nhiều thời gian hơn dự kiến và chưa hoàn tất câu trả lời. Bạn thử gửi lại câu hỏi nhé.',
      0: 'Mình chưa kết nối được với VinaBook. Bạn kiểm tra mạng rồi thử gửi lại câu hỏi nhé; nội dung đã hiển thị vẫn còn trong cuộc trò chuyện.'
    };
    return new Error(messages[status] || 'Mình chưa xử lý xong câu hỏi này. Bạn thử gửi lại nhé để mình kiểm tra tiếp.');
  }

  private fromResponse(response: ChatApiResponse): ChatMessage {
    return {
      from: 'bot',
      text: response.answer,
      products: response.products || [],
      chips: response.chips || [],
      actions: response.actions || [],
      source: response.fallback ? 'local' : 'ai',
      provider: response.provider,
      fallback: response.fallback,
      messageId: response.messageId,
      intent: response.intent,
      execution: response.execution,
      streaming: false
    };
  }

  private parseEvent(frame: string): { name: string; data: any } | null {
    let name = 'message';
    const data: string[] = [];
    frame.split(/\r?\n/).forEach(line => {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      if (line.startsWith('data:')) data.push(line.slice(5).trim());
    });
    if (!data.length) return null;
    try {
      return { name, data: JSON.parse(data.join('\n')) };
    } catch {
      return { name, data: { text: data.join('\n') } };
    }
  }

  private headers(): Record<string, string> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' };
    try {
      const saved = JSON.parse(localStorage.getItem('currentUser') || '{}');
      if (saved.token) headers.Authorization = 'Bearer ' + saved.token;
    } catch { /* anonymous chat */ }
    return headers;
  }

  private contextProductIds(history: ChatMessage[]): number[] {
    const botMessages = history.filter(message => message.from === 'bot' && !message.private && !message.streaming && message.intent !== 'ORDER_SUPPORT');
    const latest = botMessages.length ? botMessages[botMessages.length - 1] : undefined;
    return (latest?.products || []).map(product => product.id).slice(0, 8);
  }

  resetSession(): void {
    this.ephemeralSessionIds.delete(this.sessionKey);
    try { localStorage.removeItem(this.sessionKey); } catch {}
    try { sessionStorage.removeItem(this.sessionKey); } catch {}
  }

  private sessionId(): string {
    let id: string | null = null;
    try { id = localStorage.getItem(this.sessionKey) || sessionStorage.getItem(this.sessionKey); } catch {}
    if (!id) id = this.ephemeralSessionIds.get(this.sessionKey) || null;
    if (this.sessionKey.endsWith(':guest') && id && !/^chat-[0-9a-f]{48}$/.test(id)) id = null;
    if (!id) {
      const bytes = new Uint8Array(24);
      window.crypto.getRandomValues(bytes);
      id = 'chat-' + Array.from(bytes).map(byte => byte.toString(16).padStart(2, '0')).join('');
    }
    this.ephemeralSessionIds.set(this.sessionKey, id);
    try { localStorage.setItem(this.sessionKey, id); } catch {}
    return id;
  }

  private pause(milliseconds: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { environment } from 'src/environments/environment';

export type ChatIntent =
  | 'DISCOVER_BOOKS' | 'PRODUCT_INFO' | 'COMPARE_BOOKS'
  | 'POLICY' | 'ORDER_SUPPORT' | 'SMALL_TALK' | 'UNKNOWN';

export interface ChatAction {
  type: 'VIEW_PRODUCT' | 'ADD_TO_CART' | 'LOGIN' | 'VIEW_ORDERS';
  label: string;
  productId?: number;
  route?: string;
  requiresConfirmation: boolean;
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
  feedback?: 'positive' | 'negative';
}

interface ChatApiResponse {
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
  private readonly sessionKey = 'vinabook_chat_id';

  constructor(private http: HttpClient) {}

  greeting(): ChatMessage {
    return {
      from: 'bot',
      text: 'Chào bạn, mình là trợ lý mua sách VinaBook. Hãy cho mình biết chủ đề, người đọc và ngân sách; mình cũng có thể so sánh sách hoặc tra cứu đơn hàng.',
      chips: ['Gợi ý sách dưới 150k', 'Tìm sách làm quà', 'So sánh hai cuốn', 'Đơn hàng của tôi'],
      source: 'local',
      intent: 'SMALL_TALK'
    };
  }

  ask(input: string, history: ChatMessage[]): Observable<ChatMessage> {
    return new Observable<ChatMessage>(observer => {
      const controller = new AbortController();
      let fallbackSub: Subscription | undefined;
      const body = {
        message: input,
        sessionId: this.sessionId(),
		contextProductIds: this.contextProductIds(history),
        history: history
          .filter(message => message.text && (message.from === 'user' || message.from === 'bot'))
          .slice(-8)
          .map(message => ({
            role: message.from === 'user' ? 'user' : 'assistant',
            content: message.text
          }))
      };

      const run = async () => {
        try {
          const response = await fetch(this.api + '/stream', {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify(body),
            signal: controller.signal
          });
          if (!response.ok || !response.body) {
            throw new Error('stream-unavailable');
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder('utf-8');
          let buffer = '';
          let streamed = '';
          while (true) {
            const result = await reader.read();
            if (result.done) break;
            buffer += decoder.decode(result.value, { stream: true });
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() || '';
            for (const frame of frames) {
              const event = this.parseEvent(frame);
              if (!event) continue;
              if (event.name === 'chunk') {
                streamed += event.data.text || '';
                observer.next({ from: 'bot', text: streamed, streaming: true });
              } else if (event.name === 'done') {
                observer.next(this.fromResponse(event.data as ChatApiResponse));
                observer.complete();
                return;
              } else if (event.name === 'error') {
                throw new Error(event.data.message || 'chat-error');
              }
            }
          }
          throw new Error('stream-ended');
        } catch (error) {
          if (controller.signal.aborted) return;
          fallbackSub = this.http.post<ChatApiResponse>(this.api, body).subscribe({
            next: response => observer.next(this.fromResponse(response)),
            error: () => observer.error(new Error('Không thể kết nối trợ lý VinaBook')),
            complete: () => observer.complete()
          });
        }
      };

      run();
      return () => {
        controller.abort();
        fallbackSub?.unsubscribe();
      };
    });
  }

  feedback(messageId: string, value: 'positive' | 'negative'): Observable<void> {
    return this.http.post<void>(this.api + '/feedback', { messageId, value });
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
    const botMessages = history.filter(message => message.from === 'bot' && message.products?.length);
    const latest = botMessages.length ? botMessages[botMessages.length - 1] : undefined;
    return (latest?.products || []).map(product => product.id).slice(0, 8);
  }

  private sessionId(): string {
    let id = sessionStorage.getItem(this.sessionKey);
    if (!id) {
      id = 'chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
      sessionStorage.setItem(this.sessionKey, id);
    }
    return id;
  }
}

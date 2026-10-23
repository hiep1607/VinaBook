import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { environment } from 'src/environments/environment';
import { ChatMessage } from '../common/ChatMessage';
import { SessionService } from './session.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private events = new Subject<ChatMessage>();
  private socket?: WebSocket;
  private ticketRequest?: Subscription;
  private reconnect?: ReturnType<typeof setTimeout>;
  private consumers = 0;
  private attempts = 0;
  private generation = 0;
  constructor(private http: HttpClient, private session: SessionService) {}

  watch(): Observable<ChatMessage> {
    return new Observable(observer => {
      const subscription = this.events.subscribe(observer);
      this.consumers++;
      if (this.consumers === 1) this.connect();
      return () => {
        subscription.unsubscribe();
        if (--this.consumers === 0) this.disconnect();
      };
    });
  }

  private connect(): void {
    const token = this.session.getToken();
    if (!this.consumers || !token || this.socket || this.ticketRequest) return;
    const generation = this.generation;
    this.ticketRequest = this.http.post<{ticket: string}>(environment.apiUrl + '/api/notification/socket-ticket', {},
      { headers: { Authorization: 'Bearer ' + token } }).subscribe({
      next: result => {
        this.ticketRequest = undefined;
        if (!this.consumers || generation !== this.generation || token !== this.session.getToken()) return;
        const url = new URL(environment.wsUrl, window.location.href);
        if (url.protocol === 'http:') url.protocol = 'ws:';
        if (url.protocol === 'https:' || window.location.protocol === 'https:') url.protocol = 'wss:';
        url.searchParams.set('ticket', result.ticket);
        const socket = this.createSocket(url.toString());
        this.socket = socket;
        socket.onopen = () => { this.attempts = 0; };
        socket.onmessage = event => {
          if (generation !== this.generation || token !== this.session.getToken()) return;
          try {
            const message = JSON.parse(event.data);
            if (message.type === 'ORDER_CHANGED' && Number.isSafeInteger(message.orderId) && message.orderId > 0
                && typeof message.message === 'string') this.events.next({ user: 'VinaBook', message: message.message.slice(0, 500) });
          } catch { /* Ignore malformed frames without breaking the connection. */ }
        };
        socket.onclose = () => { if (this.socket === socket) { this.socket = undefined; this.retry(); } };
        socket.onerror = () => socket.close();
      },
      error: error => {
        this.ticketRequest = undefined;
        if (generation === this.generation && ![401,403].includes(error.status)) this.retry();
      }
    });
  }
  protected createSocket(url: string): WebSocket { return new WebSocket(url); }
  private retry(): void {
    if (!this.consumers || !this.session.getToken() || this.attempts >= 5 || this.reconnect) return;
    this.reconnect = setTimeout(() => { this.reconnect = undefined; this.connect(); }, Math.min(30000, 1000 * 2 ** this.attempts++));
  }
  private disconnect(): void {
    this.generation++;
    this.ticketRequest?.unsubscribe(); this.ticketRequest = undefined;
    if (this.reconnect) clearTimeout(this.reconnect);
    this.reconnect = undefined; this.attempts = 0;
    const socket = this.socket; this.socket = undefined;
    socket?.close();
  }
}

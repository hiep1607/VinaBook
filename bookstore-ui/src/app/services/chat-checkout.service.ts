import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { take, timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

export interface ChatPurchaseIntent {
  surface?: 'web' | 'chat';
  action?: 'BUY_NOW' | 'ADD_TO_CART';
  fromCart: boolean;
  productId?: number;
  quantity: number;
  returnUrl?: string;
}

export interface ChatShipping {
  voucherCode?:string;
  productId?: number;
  quantity?: number;
  items?: { productId: number; quantity: number }[];
  fromCart?: boolean;
  cartRevision?: number;
  paymentMethod: string;
  recipient: string;
  phone: string;
  street: string;
}
export interface ChatQuote extends ChatShipping {
  token: string;
  name: string;
  unitPrice: number;
  discount?:number;
  subtotal: number;
  shipping: number;
  total: number;
  expiresAt: number;
  items: { productId: number; name: string; quantity: number; unitPrice: number }[];
}

export interface ChatCartItem {
  id: number;
  name: string;
  author?: string;
  image?: string;
  price: number;
  quantity: number;
  stock?: number;
  selected?: boolean;
}

/** Only IDs, quantities and revision; shipping data never enters conversation history. */
export function chatCartFingerprint(items: ChatCartItem[], revision: number): string {
  return revision + ':' + items.map(item => `${item.id}=${item.quantity}`).sort().join(',');
}
@Injectable({ providedIn: 'root' })
export class ChatCheckoutService {
  private readonly api = environment.apiUrl + '/api/chat/checkout';
  private readonly purchaseIntentKey = 'vinabook_chat_purchase_intent_v1';
  private readonly purchaseIntentLifetime = 15 * 60 * 1000;
  constructor(private http: HttpClient) {}

  /** Resume only the book selection after an explicit login, never shipping or payment details. */
  rememberPurchaseIntent(intent: ChatPurchaseIntent): void {
    try {
      sessionStorage.removeItem(this.purchaseIntentKey);
      const selection = this.safePurchaseIntent(intent);
      if (selection) sessionStorage.setItem(this.purchaseIntentKey, JSON.stringify({ ...selection, createdAt: Date.now() }));
    } catch { /* A blocked browser store must not prevent login. */ }
  }

  consumePurchaseIntent(surface: 'web' | 'chat' = 'chat'): ChatPurchaseIntent | undefined {
    try {
      const stored = sessionStorage.getItem(this.purchaseIntentKey);
      if (!stored) return undefined;
      const intent = JSON.parse(stored);
      const age = Date.now() - intent?.createdAt;
      const selection = this.safePurchaseIntent(intent);
      if (!Number.isSafeInteger(intent?.createdAt) || age < 0 || age > this.purchaseIntentLifetime || !selection) {
        sessionStorage.removeItem(this.purchaseIntentKey); return undefined;
      }
      if ((selection.surface || 'chat') !== surface) return undefined;
      sessionStorage.removeItem(this.purchaseIntentKey);
      return selection;
    } catch { try { sessionStorage.removeItem(this.purchaseIntentKey); } catch {} return undefined; }
  }

  private safePurchaseIntent(value: any): ChatPurchaseIntent | undefined {
    if (!value || typeof value.fromCart !== 'boolean' || !Number.isSafeInteger(value.quantity)
        || value.quantity < 1 || value.quantity > 99) return undefined;
    if (!value.fromCart && (!Number.isSafeInteger(value.productId) || value.productId < 1)) return undefined;
    if (value.productId != null && (!Number.isSafeInteger(value.productId) || value.productId < 1)) return undefined;
    const selection: ChatPurchaseIntent = { fromCart: value.fromCart, quantity: value.quantity };
    if (value.surface === 'web') {
      if (value.fromCart || !['BUY_NOW', 'ADD_TO_CART'].includes(value.action)) return undefined;
      selection.surface = 'web'; selection.action = value.action;
    } else if (value.surface != null && value.surface !== 'chat') return undefined;
    if (!value.fromCart) selection.productId = value.productId;
    if (typeof value.returnUrl === 'string' && value.returnUrl.length <= 2000
        && /^\/(?!\/)/.test(value.returnUrl) && !/[\\\u0000-\u001f\u007f]/.test(value.returnUrl)) {
      selection.returnUrl = value.returnUrl;
    }
    return selection;
  }

  quote(input: ChatShipping) { return this.http.post<ChatQuote>(this.api + '/quote', input).pipe(timeout(15000), take(1)); }
  cancelDraft(token: string) { return this.http.delete<void>(this.api + '/' + encodeURIComponent(token)).pipe(timeout(15000), take(1)); }
  confirm(token: string) {
    return this.http.post<{orderId: number}>(this.api + '/' + encodeURIComponent(token) + '/confirm', {}).pipe(timeout(20000), take(1));
  }
}

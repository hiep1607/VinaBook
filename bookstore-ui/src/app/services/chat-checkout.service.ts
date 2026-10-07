import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';

export interface ChatShipping {
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
  constructor(private http: HttpClient) {}
  quote(input: ChatShipping) { return this.http.post<ChatQuote>(this.api + '/quote', input); }
  cancelDraft(token: string) { return this.http.delete<void>(this.api + '/' + encodeURIComponent(token)); }
  confirm(token: string) {
    return this.http.post<{orderId: number}>(this.api + '/' + encodeURIComponent(token) + '/confirm', {});
  }
}

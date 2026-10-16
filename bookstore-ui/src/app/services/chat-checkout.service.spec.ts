import { fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ChatCheckoutService } from './chat-checkout.service';

describe('Chat checkout purchase resume', () => {
  const key = 'vinabook_chat_purchase_intent_v1';
  let original: string | null;
  let http: any;
  let service: ChatCheckoutService;

  beforeEach(() => {
    original = sessionStorage.getItem(key);
    sessionStorage.removeItem(key);
    http = { post: jasmine.createSpy('post'), delete: jasmine.createSpy('delete') };
    service = new ChatCheckoutService(http);
  });

  afterEach(() => {
    if (original === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, original);
  });

  it('stores only the selected book, quantity and local return route and consumes it once', () => {
    service.rememberPurchaseIntent({ fromCart: false, productId: 7, quantity: 2, returnUrl: '/product/7?tab=details',
      recipient: 'Private recipient', phone: '0901234567', street: 'Private street', token: 'Private token' } as any);
    const stored = JSON.parse(sessionStorage.getItem(key)!);
    expect(Object.keys(stored).sort()).toEqual(['createdAt', 'fromCart', 'productId', 'quantity', 'returnUrl']);
    expect(service.consumePurchaseIntent()).toEqual({ fromCart: false, productId: 7, quantity: 2, returnUrl: '/product/7?tab=details' });
    expect(service.consumePurchaseIntent()).toBeUndefined();
    expect(sessionStorage.getItem(key)).toBeNull();
  });

  it('allows cart resume without retaining an unrelated book or private fields', () => {
    service.rememberPurchaseIntent({ fromCart: true, productId: 7, quantity: 1, phone: '0901234567' } as any);
    expect(service.consumePurchaseIntent()).toEqual({ fromCart: true, quantity: 1 });
  });

  it('rejects malformed, expired or future selections and removes them permanently', () => {
    const base = { fromCart: false, productId: 7, quantity: 1, createdAt: Date.now() };
    const invalid = [
      { ...base, createdAt: Date.now() - 15 * 60 * 1000 - 1000 },
      { ...base, createdAt: Date.now() + 60000 },
      { ...base, quantity: 0 }, { ...base, quantity: 100 }, { ...base, quantity: 1.5 },
      { ...base, productId: -1 }, { ...base, productId: '7' }, { ...base, fromCart: 'false' }
    ];
    for (const value of invalid) {
      sessionStorage.setItem(key, JSON.stringify(value));
      expect(service.consumePurchaseIntent()).toBeUndefined();
      expect(sessionStorage.getItem(key)).toBeNull();
    }
    sessionStorage.setItem(key, '{invalid');
    expect(service.consumePurchaseIntent()).toBeUndefined();
    expect(sessionStorage.getItem(key)).toBeNull();
  });

  it('drops external or ambiguous return URLs and rejects invalid replacement selections', () => {
    for (const returnUrl of ['https://external.invalid/', '//external.invalid/', '/\\external.invalid', '/\nexternal.invalid']) {
      service.rememberPurchaseIntent({ fromCart: false, productId: 7, quantity: 1, returnUrl });
      expect(service.consumePurchaseIntent()).toEqual({ fromCart: false, productId: 7, quantity: 1 });
    }
    service.rememberPurchaseIntent({ fromCart: false, productId: 7, quantity: 1 });
    service.rememberPurchaseIntent({ fromCart: false, productId: 0, quantity: 1 });
    expect(service.consumePurchaseIntent()).toBeUndefined();
  });

  it('times out quotes and confirmation while keeping the original confirmation token available to the caller', fakeAsync(() => {
    http.post.and.returnValue(new Subject());
    let quoteError: any;
    let confirmError: any;
    service.quote({ productId: 7, quantity: 1, recipient: 'QA', phone: '0901234567', street: 'QA', paymentMethod: 'COD' })
      .subscribe({ error: error => quoteError = error });
    service.confirm('same-token').subscribe({ error: error => confirmError = error });
    tick(15000);
    expect(quoteError.name).toBe('TimeoutError');
    expect(confirmError).toBeUndefined();
    tick(5000);
    expect(confirmError.name).toBe('TimeoutError');
    expect(http.post.calls.mostRecent().args[0]).toContain('/same-token/confirm');
  }));
});

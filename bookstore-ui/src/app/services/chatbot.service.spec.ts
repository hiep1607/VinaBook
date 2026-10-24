import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ChatbotService } from './chatbot.service';

describe('ChatbotService transport recovery', () => {
  let service: ChatbotService;
  let http: HttpTestingController;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ChatbotService);
    http = TestBed.inject(HttpTestingController);
    fetchSpy = spyOn(window, 'fetch');
    spyOn(window, 'matchMedia').and.returnValue({ matches: false } as MediaQueryList);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));

  function streamFrame(frame: string): void {
    const reader = { read: jasmine.createSpy('read').and.returnValues(
      Promise.resolve({ done: false, value: new TextEncoder().encode(frame) }),
      new Promise(() => {})
    ) };
    fetchSpy.and.returnValue(Promise.resolve({ ok: true, status: 200,
      body: { getReader: () => reader } } as unknown as Response));
  }

  it('restores only safe actions with current product stock and price', () => {
    let restored: any;
    service.restoreActions({ from: 'bot', text: 'Chọn sách', products: [{ id: 7, name: 'Cũ', author: '', category: '', price: 50000, oldPrice: 50000, discount: 0, quantity: 8, image: '' }],
      actions: [{type: 'LOGIN', label: 'Đăng nhập để đặt hàng', productId: 7, quantity: 1, requiresConfirmation: false},
        {type: 'CANCEL_ORDER', label: 'Hủy đơn', orderId: 1, requiresConfirmation: true}] }).subscribe(message => restored = message);
    http.expectOne('/api/products').flush([{productId:7, name:'Sách mới', author:'Tác giả', price:100000, discount:10, quantity:2}]);
    expect(restored.products[0].price).toBe(90000);expect(restored.products[0].quantity).toBe(2);
    expect(restored.actions.map((action: any) => action.type)).toEqual(['LOGIN']);
  });
  it('does not restore a buy action for an unavailable book', () => {
    let restored: any;
    service.restoreActions({from:'bot',text:'Mua sách',products:[{id:7,name:'Sách',author:'',category:'',price:10000,oldPrice:10000,discount:0,quantity:2,image:''}],
      actions:[{type:'BUY_NOW',label:'Mua ngay',productId:7,requiresConfirmation:false}]}).subscribe(message => restored=message);
    http.expectOne('/api/products').flush([]);expect(restored.actions).toEqual([]);
  });
  it('restores the in-chat cart view after reload without restoring a cart mutation', () => {
    let restored: any;
    service.restoreActions({from:'bot',text:'Giỏ của bạn',actions:[
      {type:'SHOW_CART',label:'Xem và sửa giỏ',requiresConfirmation:false},
      {type:'CLEAR_CART',label:'Xóa giỏ',requiresConfirmation:true}
    ]}).subscribe(message => restored=message);
    http.expectOne('/api/products').flush([]);
    expect(restored.actions.map((action: any) => action.type)).toEqual(['SHOW_CART']);
  });

  it('stops waiting when fetch never returns headers', fakeAsync(() => {
    fetchSpy.and.returnValue(new Promise(() => {}));
    let failure = '';
    service.ask('Giỏ hàng của tôi', []).subscribe({ error: error => failure = error.message });
    tick(60000);
    expect(failure).toContain('chưa hoàn tất câu trả lời');
    expect(fetchSpy.calls.mostRecent().args[1].signal.aborted).toBeTrue();
    http.expectNone('/api/chat');
  }));

  it('stops waiting when SSE sends ready but never finishes', fakeAsync(() => {
    streamFrame('event:ready\ndata:{"status":"thinking"}\n\n');
    let failure = '';
    service.ask('Giỏ hàng của tôi', []).subscribe({ error: error => failure = error.message });
    flushMicrotasks();
    tick(60000);
    expect(failure).toContain('chưa hoàn tất câu trả lời');
    http.expectNone('/api/chat');
  }));

  it('reports SSE errors immediately without repeating the request', fakeAsync(() => {
    streamFrame('event:error\ndata:{"message":"Vui lòng thử lại."}\n\n');
    let failure = '';
    service.ask('Đơn hàng của tôi', []).subscribe({ error: error => failure = error.message });
    flushMicrotasks();
    expect(failure).toBe('Vui lòng thử lại.');
    tick(60000);
    http.expectNone('/api/chat');
  }));

  it('bounds the JSON fallback when streaming cannot connect', fakeAsync(() => {
    fetchSpy.and.returnValue(Promise.reject(new Error('network')));
    let failure = '';
    service.ask('Giỏ hàng của tôi', []).subscribe({ error: error => failure = error.message });
    flushMicrotasks();
    const fallback = http.expectOne('/api/chat');
    tick(60000);
    expect(failure).toContain('chưa hoàn tất câu trả lời');
    expect(fallback.cancelled).toBeTrue();
  }));

  it('reveals a completed response and cancels its waiting timer', fakeAsync(() => {
    streamFrame('event:done\ndata:{"answer":"Giỏ hàng sẵn sàng","provider":"local","fallback":false}\n\n');
    let completed = false;
    let failure = false;
    let text = '';
    service.ask('Giỏ hàng của tôi', []).subscribe({ next: message => text = message.text,
      complete: () => completed = true, error: () => failure = true });
    flushMicrotasks();
    tick(1000);
    expect(text).toBe('Giỏ hàng sẵn sàng');
    expect(completed).toBeTrue();
    tick(60000);
    expect(failure).toBeFalse();
  }));

  it('reveals Vietnamese text progressively and exposes actions only on completion', fakeAsync(() => {
    const response = { answer: 'Mình gợi ý một vài cuốn sách phù hợp với bạn nhé. 📚',
      provider: 'gemini', fallback: false, messageId: 'answer-1', intent: 'DISCOVER_BOOKS',
      products: [{ id: 7 }], chips: ['Tình bạn'],
      actions: [{ type: 'VIEW_PRODUCT', productId: 7, label: 'Xem sách' }] };
    streamFrame('event:done\ndata:' + JSON.stringify(response) + '\n\n');
    const messages: any[] = [];
    let completed = false;
    service.ask('Gợi ý sách', []).subscribe({ next: message => messages.push(message), complete: () => completed = true });
    flushMicrotasks();
    expect(messages.length).toBe(1);
    expect(messages[0].text.length).toBeGreaterThan(0);
    expect(messages[0].text.length).toBeLessThan(response.answer.length);
    expect(messages[0].streaming).toBeTrue();
    expect(messages[0].actions).toBeUndefined();
    expect(messages[0].products).toBeUndefined();
    expect(messages[0].chips).toBeUndefined();
    expect(messages[0].messageId).toBeUndefined();
    expect(completed).toBeFalse();
    tick(48);
    expect(messages.length).toBeGreaterThan(1);
    expect(messages[messages.length - 1].text.length).toBeLessThan(response.answer.length);
    tick(5000);
    const final = messages[messages.length - 1];
    expect(final.text).toBe(response.answer);
    expect(final.streaming).toBeFalse();
    expect(final.products).toEqual(response.products);
    expect(final.actions).toEqual(response.actions);
    expect(final.chips).toEqual(response.chips);
    expect(final.messageId).toBe('answer-1');
    expect(completed).toBeTrue();
    messages.forEach(message => expect(/[\uD800-\uDBFF]$/.test(message.text)).toBeFalse());
    http.expectNone('/api/chat');
  }));

  it('also reveals the JSON fallback progressively without sending a second turn', fakeAsync(() => {
    fetchSpy.and.returnValue(Promise.reject(new Error('stream unavailable')));
    const messages: any[] = [];
    let completed = false;
    service.ask('Tìm sách', []).subscribe({ next: message => messages.push(message), complete: () => completed = true });
    flushMicrotasks();
    http.expectOne('/api/chat').flush({ answer: 'Đây là những cuốn sách bạn có thể tham khảo.', provider: 'gemini', fallback: false });
    flushMicrotasks();
    expect(messages[0].streaming).toBeTrue();
    expect(completed).toBeFalse();
    tick(5000);
    expect(messages[messages.length - 1].text).toBe('Đây là những cuốn sách bạn có thể tham khảo.');
    expect(messages[messages.length - 1].streaming).toBeFalse();
    expect(completed).toBeTrue();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  }));

  it('stops revealing on unsubscribe without leaking a finished answer to another account', fakeAsync(() => {
    streamFrame('event:done\ndata:{"answer":"Nội dung đang trả lời cho tài khoản hiện tại.","provider":"gemini","fallback":false}\n\n');
    const messages: any[] = [];
    let completed = false;
    const request = service.ask('Tìm sách', []).subscribe({ next: message => messages.push(message), complete: () => completed = true });
    flushMicrotasks();
    expect(messages[0].streaming).toBeTrue();
    const count = messages.length;
    request.unsubscribe();
    tick(60000);
    expect(messages.length).toBe(count);
    expect(completed).toBeFalse();
    http.expectNone('/api/chat');
  }));

  it('notifies Angular for each reveal frame when fetch resolves outside its zone', fakeAsync(() => {
    streamFrame('event:done\ndata:{"answer":"Một câu trả lời cần hiện dần.","provider":"gemini","fallback":false}\n\n');
    const zone = TestBed.inject(NgZone);
    const zones: boolean[] = [];
    zone.runOutsideAngular(() => service.ask('Tìm sách', []).subscribe(message => zones.push(NgZone.isInAngularZone())));
    flushMicrotasks();
    tick(5000);
    expect(zones.length).toBeGreaterThan(1);
    expect(zones.every(inZone => inZone)).toBeTrue();
  }));

  it('respects reduced motion by showing the validated answer immediately', fakeAsync(() => {
    (window.matchMedia as jasmine.Spy).and.returnValue({ matches: true } as MediaQueryList);
    streamFrame('event:done\ndata:{"answer":"Gợi ý sách cho bạn.","provider":"gemini","fallback":false}\n\n');
    const messages: any[] = [];
    let completed = false;
    service.ask('Gợi ý sách', []).subscribe({ next: message => messages.push(message), complete: () => completed = true });
    flushMicrotasks();
    expect(messages.length).toBe(1);
    expect(messages[0].text).toBe('Gợi ý sách cho bạn.');
    expect(messages[0].streaming).toBeFalse();
    expect(completed).toBeTrue();
    tick(60000);
  }));

  it('aborts pending transport when the account or widget unsubscribes', fakeAsync(() => {
    fetchSpy.and.returnValue(new Promise(() => {}));
    let failure = false;
    const request = service.ask('Giỏ hàng của tôi', []).subscribe({ error: () => failure = true });
    request.unsubscribe();
    tick(60000);
    expect(fetchSpy.calls.mostRecent().args[1].signal.aborted).toBeTrue();
    expect(failure).toBeFalse();
  }));
});

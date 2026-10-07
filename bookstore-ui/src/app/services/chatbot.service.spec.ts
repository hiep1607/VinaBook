import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
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

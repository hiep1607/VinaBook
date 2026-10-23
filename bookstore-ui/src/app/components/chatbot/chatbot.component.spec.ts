import { CommonModule } from '@angular/common';
import { Component, forwardRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { ChatbotService, ChatMessage } from 'src/app/services/chatbot.service';
import { FabCollisionService } from 'src/app/services/fab-collision.service';
import { WishlistService } from 'src/app/services/wishlist.service';
import { ReviewService } from 'src/app/services/review.service';
import { BookReviewComponent } from '../book-review/book-review.component';
import { ChatbotComponent } from './chatbot.component';
import { ChatCommerceComponent } from '../chat-commerce/chat-commerce.component';
import { ChatCheckoutService } from 'src/app/services/chat-checkout.service';

@Component({
  selector: 'app-chat-commerce', template: '<div class="commerce-fixture"></div>',
  providers: [{ provide: ChatCommerceComponent, useExisting: forwardRef(() => ChatCommerceFixture) }]
})
class ChatCommerceFixture {
  checkout: any;
  uncertainConfirmation = false;
  error = '';
  reset = jasmine.createSpy('reset').and.callFake(() => { this.checkout = undefined; this.error = ''; });
  resumePurchase = jasmine.createSpy('resumePurchase').and.returnValue(Promise.resolve());
  handleCheckoutMessage = jasmine.createSpy('handleCheckoutMessage').and.returnValue(Promise.resolve(true));
  runAction = jasmine.createSpy('runAction');
  startCheckout = jasmine.createSpy('startCheckout').and.callFake((fromCart: boolean, product: any, quantity: number) => {
    this.checkout = { fromCart, product, quantity }; return Promise.resolve();
  });
  login = jasmine.createSpy('login');
  cancelChange = () => {};
  dismiss = () => {};
  showCart = () => Promise.resolve();
}

describe('Chatbot purchase actions', () => {
  let fixture: ComponentFixture<ChatbotComponent>;
  let replies: Subject<ChatMessage>;
  let originalCache: string | null;
  let originalReviewCache: string | null;
  let auth: any;
  let reviews: any;
  let checkout: any;
  let chatbot: any;
  const cache = 'vinabook_chat_session_v2:guest';
  const reviewCache = 'vinabook_chat_session_v2:user:' + encodeURIComponent('book-review-qa@test.invalid');
  const purchase: ChatMessage = {
    from: 'bot', text: 'Bạn bấm Tiếp tục đặt hàng để chọn thông tin giao hàng.', intent: 'PURCHASE',
    products: [{ id: 7, name: 'Luật Đất Đai', author: 'Quốc Hội', category: 'Pháp luật',
      price: 110000, oldPrice: 110000, discount: 0, quantity: 80, image: '' }],
    actions: [{ type: 'BUY_NOW', label: 'Tiếp tục đặt hàng', productId: 7, quantity: 1, requiresConfirmation: false }]
  };

  beforeEach(async () => {
    originalCache = localStorage.getItem(cache);
    originalReviewCache = localStorage.getItem(reviewCache);
    localStorage.removeItem(cache);
    localStorage.removeItem(reviewCache);
    replies = new Subject<ChatMessage>();
    auth = { currentUser: undefined, isLoggedIn: () => !!auth.currentUser?.token };
    reviews = { changes$: new BehaviorSubject(undefined), getUserReview: () => null,
      upsertReview: jasmine.createSpy('upsertReview').and.returnValue(of({})), refresh: () => {} };
    checkout = { consumePurchaseIntent: jasmine.createSpy('consumePurchaseIntent').and.returnValue(undefined) };
    chatbot = { useAccount: () => {}, history: () => of([]), restoreActions: (message: ChatMessage) => of(message),
      greeting: () => ({ from: 'bot', text: 'Chào bạn' }), ask: jasmine.createSpy('ask').and.returnValue(replies) };
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule], declarations: [ChatbotComponent, BookReviewComponent, ChatCommerceFixture], schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ChatbotService, useValue: chatbot },
        { provide: ChatCheckoutService, useValue: checkout },
        { provide: CartService, useValue: { syncing: false, serverRevision: 0, getCartItems: () => [] } },
        { provide: AuthService, useValue: auth },
        { provide: ReviewService, useValue: reviews },
        { provide: WishlistService, useValue: { getIds: () => [7], addToWishlist: jasmine.createSpy(), removeFromWishlist: jasmine.createSpy() } },
        { provide: Router, useValue: { url: '/', navigate: jasmine.createSpy(), navigateByUrl: jasmine.createSpy() } },
        { provide: FabCollisionService, useValue: { watch: () => of(0), recheck: () => {} } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ChatbotComponent);
    fixture.componentInstance.open = true;
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    if (originalCache === null) localStorage.removeItem(cache); else localStorage.setItem(cache, originalCache);
    if (originalReviewCache === null) localStorage.removeItem(reviewCache); else localStorage.setItem(reviewCache, originalReviewCache);
  });

  it('restores the login-to-buy button after loading saved server history', () => {
    const restored: ChatMessage = {...purchase, execution:{tool:'prepareCheckout',status:'NEEDS_INPUT'},
      actions:[{type:'LOGIN',label:'Đăng nhập để đặt hàng',productId:7,quantity:1,requiresConfirmation:false}]};
    chatbot.history = () => of([{from:'user',text:'Mua cuốn này'},restored]);
    fixture.destroy();fixture=TestBed.createComponent(ChatbotComponent);fixture.detectChanges();
    const button=fixture.nativeElement.querySelector('.bot-product button.primary') as HTMLButtonElement;
    expect(button?.textContent).toContain('Đăng nhập để đặt hàng');
    expect(button.disabled).toBeFalse();
  });

  it('renders the current BUY_NOW action and opens commerce with the selected book', () => {
    fixture.componentInstance.send('đặt cho tôi cuốn luật đất đai');
    replies.next({ from: 'bot', text: 'Đang chuẩn bị', streaming: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.bot-product button.primary')).toBeNull();
    replies.next(purchase);
    replies.complete();
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.bot-product button.primary');
    expect(button.textContent?.trim()).toBe('Tiếp tục đặt hàng');
    const runAction = jasmine.createSpy('runAction');
    fixture.componentInstance.commerce = { runAction } as any;
    button.click();
    expect(runAction).toHaveBeenCalledWith(purchase.actions![0], purchase.products![0], jasmine.any(String));
  });

  it('does not execute an old cached purchase action without a current cart snapshot', () => {
    fixture.componentInstance.messages = [purchase];
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.bot-product button.primary')).toBeNull();
    expect(fixture.nativeElement.querySelector('.bot-product button').textContent).toContain('Xem sách');
  });
  it('continues a resolved guest purchase through login with the selected quantity and no extra action', async () => {
    const reply: ChatMessage = { ...purchase, execution: { tool: 'prepareCheckout', status: 'AWAITING_INPUT' }, actions: [
      { type: 'LOGIN', label: 'Đăng nhập để đặt hàng', route: '/login', productId: 7, quantity: 2, requiresConfirmation: false }
    ] };
    fixture.componentInstance.send('Tôi muốn mua 2 cuốn Luật Đất Đai'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    const count = fixture.componentInstance.messages.length;
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.bot-product button.primary');
    expect(button.textContent?.trim()).toBe('Đăng nhập để đặt hàng');
    button.click(); button.click(); await fixture.whenStable();
    expect(commerce.startCheckout).toHaveBeenCalledOnceWith(false, reply.products![0], 2);
    expect(commerce.login).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(Router).navigateByUrl).not.toHaveBeenCalled();
    expect(fixture.componentInstance.messages.length).toBe(count);
    expect(chatbot.ask).toHaveBeenCalledTimes(1);
  });
  it('does not navigate or retain a stale or invalid purchase login action', async () => {
    const reply: ChatMessage = { ...purchase, execution: { tool: 'prepareCheckout', status: 'AWAITING_INPUT' }, actions: [
      { type: 'LOGIN', label: 'Đăng nhập để đặt hàng', route: '/login', productId: 7, quantity: 0, requiresConfirmation: false }
    ] };
    fixture.componentInstance.send('Mua cuốn này'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    await fixture.componentInstance.runAction(reply.actions![0], reply);
    reply.actions![0].quantity = 2;
    fixture.componentInstance.messages.push({ from: 'bot', text: 'Câu trả lời tiếp theo' });
    await fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(commerce.startCheckout).not.toHaveBeenCalled();
    expect(commerce.login).not.toHaveBeenCalled();
    expect(TestBed.inject(Router).navigateByUrl).not.toHaveBeenCalled();
  });
  it('preserves ordinary login navigation for a book review instead of creating a purchase', async () => {
    const reply: ChatMessage = { ...purchase, intent: 'PRODUCT_INFO', execution: { tool: 'bookReviews', status: 'NEEDS_INPUT' }, actions: [
      { type: 'LOGIN', label: 'Đăng nhập để viết đánh giá', route: '/login', productId: 7, requiresConfirmation: false }
    ] };
    fixture.componentInstance.send('Viết đánh giá'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    await fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledOnceWith('/login');
    expect(commerce.startCheckout).not.toHaveBeenCalled();
    expect(commerce.login).not.toHaveBeenCalled();
  });
  it('offers buy now next to add to cart and opens the same checkout with the selected quantity', () => {
    const reply: ChatMessage = { ...purchase, actions: [
      { type: 'ADD_TO_CART', label: 'Thêm giỏ', productId: 7, quantity: 2, requiresConfirmation: true }
    ] };
    fixture.componentInstance.send('Giới thiệu cuốn này'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.bp-actions button'));
    expect(buttons.map(button => button.textContent?.trim())).toEqual(['Xem sách', 'Thêm giỏ', 'Mua ngay']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.bp-actions button')[2]).toBe(buttons[2]);
    const runAction = jasmine.createSpy('runAction'); fixture.componentInstance.commerce = { runAction } as any;
    buttons[2].click();
    expect(runAction).toHaveBeenCalledWith(jasmine.objectContaining({ type: 'BUY_NOW', productId: 7, quantity: 2 }),
      reply.products![0], jasmine.any(String));
    expect(reply.actions!.length).toBe(1);
  });
  it('does not duplicate a server buy-now action or offer one for unavailable stock', () => {
    const add = { type: 'ADD_TO_CART' as const, label: 'Thêm giỏ', productId: 7, quantity: 1, requiresConfirmation: true };
    const reply: ChatMessage = { ...purchase, actions: [add, purchase.actions![0]] };
    fixture.componentInstance.send('Chọn cuốn này'); replies.next(reply); replies.complete(); fixture.detectChanges();
    expect(fixture.componentInstance.productActions(reply, 7).filter(action => action.type === 'BUY_NOW').length).toBe(1);
    reply.actions = [add]; reply.products = [{ ...purchase.products![0], quantity: 0 }];
    expect(fixture.componentInstance.productActions(reply, 7).some(action => action.type === 'BUY_NOW')).toBeFalse();
  });
  it('passes an explicit add-to-cart card click directly while retaining normal action confirmation', () => {
    const reply: ChatMessage = { ...purchase, actions: [
      { type: 'ADD_TO_CART', label: 'Thêm giỏ', productId: 7, quantity: 2, requiresConfirmation: true }
    ] };
    fixture.componentInstance.send('Chọn sách'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    fixture.nativeElement.querySelectorAll('.bp-actions button')[1].click();
    expect(commerce.runAction).toHaveBeenCalledWith(reply.actions![0], reply.products![0], jasmine.any(String), true);
    commerce.runAction.calls.reset();
    fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(commerce.runAction).toHaveBeenCalledWith(reply.actions![0], reply.products![0], jasmine.any(String));
  });
  it('allows adding the current card after Undo using the current cart revision without refreshing other actions', () => {
    const reply: ChatMessage = { ...purchase, actions: [
      { type: 'ADD_TO_CART', label: 'Thêm giỏ', productId: 7, quantity: 1, requiresConfirmation: true },
      { type: 'CLEAR_CART', label: 'Xóa giỏ', requiresConfirmation: true }
    ] };
    fixture.componentInstance.send('Chọn sách'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    fixture.componentInstance.addedProductIds.add(7);
    fixture.detectChanges();
    const add: HTMLButtonElement = fixture.nativeElement.querySelectorAll('.bp-actions button')[1];
    expect(add.disabled).toBeTrue();
    const cart = TestBed.inject(CartService) as any;
    cart.serverRevision = 2; // Add then Undo restored the items, but advanced revision.
    fixture.debugElement.query(By.directive(ChatCommerceFixture)).triggerEventHandler('undone', undefined);
    fixture.detectChanges();
    expect(add.disabled).toBeFalse();
    add.click();
    expect(commerce.runAction).toHaveBeenCalledWith(reply.actions![0], reply.products![0], '2:', true);
    commerce.runAction.calls.reset();
    fixture.componentInstance.runAction(reply.actions![1], reply);
    expect(commerce.runAction).toHaveBeenCalledWith(reply.actions![1], undefined, '0:');
    commerce.runAction.calls.reset();
    fixture.componentInstance.messages.push({ from: 'bot', text: 'Câu trả lời mới' });
    fixture.componentInstance.runAction(reply.actions![0], reply, true);
    expect(commerce.runAction).not.toHaveBeenCalled();
  });
  it('keeps the purchase component and draft when minimizing while removing the panel from focus', () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    commerce.checkout = { productId: 7, quantity: 2, form: { street: 'Địa chỉ chỉ trong đơn' } };
    const draft = commerce.checkout;
    const input: HTMLTextAreaElement = fixture.nativeElement.querySelector('#chat-message');
    input.focus();
    component.toggle(); fixture.detectChanges();
    const panel: HTMLElement = fixture.nativeElement.querySelector('.chat-panel');
    expect(panel.hidden).toBeTrue();
    expect(getComputedStyle(panel).display).toBe('none');
    expect(document.activeElement).not.toBe(input);
    expect(fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance).toBe(commerce);
    expect(commerce.checkout).toBe(draft);
    expect(commerce.reset).not.toHaveBeenCalled();
    component.toggle(); fixture.detectChanges();
    expect(panel.hidden).toBeFalse();
    expect(commerce.checkout).toBe(draft);
  });
  it('delegates checkout text without adding phone or address to transcript, storage or chatbot requests', async () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    commerce.checkout = { productId: 7 };
    const count = component.messages.length;
    const saved = localStorage.getItem(cache);
    component.draft = 'đổi địa chỉ thành 12 Đường QA, số điện thoại 0901234567';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chat-input button').disabled).toBeFalse();
    component.send();
    await fixture.whenStable();
    expect(commerce.handleCheckoutMessage).toHaveBeenCalledOnceWith('đổi địa chỉ thành 12 Đường QA, số điện thoại 0901234567');
    expect(component.messages.length).toBe(count);
    expect(localStorage.getItem(cache)).toBe(saved);
    expect(chatbot.ask).not.toHaveBeenCalled();
    expect(component.draft).toBe('');
  });
  it('guides unsupported checkout text and keeps an unknown order confirmation protected', async () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    commerce.checkout = { productId: 7 };
    commerce.handleCheckoutMessage.and.returnValue(Promise.resolve(false));
    component.send('gợi ý sách khác'); await fixture.whenStable();
    expect(commerce.error).toContain('đóng đơn trước');
    commerce.handleCheckoutMessage.calls.reset();
    commerce.uncertainConfirmation = true;
    component.send('đổi thành 2 cuốn');
    expect(commerce.handleCheckoutMessage).not.toHaveBeenCalled();
    expect(commerce.error).toContain('kiểm tra kết quả');
    expect(chatbot.ask).not.toHaveBeenCalled();
  });
  it('resumes the explicit guest purchase once across login and view initialization and resets private draft on an owner switch', async () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    const intent = { fromCart: false, productId: 7, quantity: 2, returnUrl: '/' };
    checkout.consumePurchaseIntent.and.returnValues(intent, undefined);
    component.open = false;
    component.ngDoCheck();
    expect(checkout.consumePurchaseIntent).not.toHaveBeenCalled();
    auth.currentUser = { email: 'book-review-qa@test.invalid', token: 'fixture' };
    component.ngDoCheck();
    expect(commerce.reset).toHaveBeenCalledTimes(1);
    expect(commerce.resumePurchase).toHaveBeenCalledOnceWith(intent);
    expect(component.open).toBeTrue();
    component.ngDoCheck();
    await fixture.whenStable(); // The pending ngAfterViewInit timer also checks resume.
    expect(commerce.resumePurchase).toHaveBeenCalledTimes(1);
    commerce.checkout = { form: { street: 'Địa chỉ của chủ cũ' } };
    component.draft = 'địa chỉ: Nội dung chưa gửi của chủ cũ';
    auth.currentUser = { email: 'another-owner@test.invalid', token: 'fixture' };
    component.ngDoCheck();
    expect(commerce.reset).toHaveBeenCalledTimes(2);
    expect(commerce.checkout).toBeUndefined();
    expect(component.draft).toBe('');
    expect(commerce.resumePurchase).toHaveBeenCalledTimes(1);
  });
  it('resumes a pending purchase when the widget view is initialized after authentication', async () => {
    fixture.destroy();
    auth.currentUser = { email: 'book-review-qa@test.invalid', token: 'fixture' };
    const intent = { fromCart: true, quantity: 1 };
    checkout.consumePurchaseIntent.and.returnValues(intent, undefined);
    fixture = TestBed.createComponent(ChatbotComponent);
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    expect(commerce.resumePurchase).toHaveBeenCalledOnceWith(intent);
    expect(fixture.componentInstance.open).toBeTrue();
  });
  it('opens shared cancellation for the current reply without cancelling by itself', () => {
    const reply: ChatMessage = { from: 'bot', text: 'Bạn kiểm tra đơn trước khi hủy nhé.', intent: 'ORDER_SUPPORT',
      actions: [{ type: 'CANCEL_ORDER', orderId: 7, label: 'Kiểm tra và hủy đơn #7', requiresConfirmation: true }] };
    fixture.componentInstance.send('hủy đơn #7'); replies.next(reply); replies.complete(); fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('.message-actions button'); button.click(); fixture.detectChanges();
    expect(fixture.componentInstance.cancellationOrderId).toBe(7);
    expect(fixture.nativeElement.querySelector('app-order-cancellation')).not.toBeNull();
  });
  it('blocks a cancellation action restored from old history', () => {
    const reply: ChatMessage = { from: 'bot', text: 'Hủy đơn', actions: [{ type: 'CANCEL_ORDER', orderId: 7, label: 'Hủy', requiresConfirmation: true }] };
    fixture.componentInstance.messages = [reply]; fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(fixture.componentInstance.cancellationOrderId).toBe(0);
  });
  it('uses the website wishlist service and makes a repeated click harmless', () => {
    TestBed.inject(AuthService).currentUser = { email: 'buyer@test.invalid' };
    fixture.componentInstance.ngDoCheck();
    const reply: ChatMessage = { ...purchase, actions: [{ type: 'ADD_TO_WISHLIST', label: 'Lưu yêu thích', productId: 7, requiresConfirmation: false }] };
    fixture.componentInstance.send('Lưu cuốn này vào yêu thích'); replies.next(reply); replies.complete(); fixture.detectChanges();
    fixture.componentInstance.runAction(reply.actions![0], reply); fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(TestBed.inject(WishlistService).addToWishlist).toHaveBeenCalledOnceWith('buyer@test.invalid', 7);
  });
  it('opens a support draft for review and does not send the conversation automatically', () => {
    const reply: ChatMessage = { from: 'bot', text: 'Bạn xem lại trước khi gửi nhé.', actions: [{ type: 'CREATE_SUPPORT_REQUEST', orderId: null as any, label: 'Soạn yêu cầu', requiresConfirmation: true }] };
    fixture.componentInstance.send('Nhờ nhân viên hỗ trợ giao sách'); replies.next(reply); replies.complete(); fixture.detectChanges();
    fixture.componentInstance.runAction(reply.actions![0], reply); fixture.detectChanges();
    expect(fixture.componentInstance.supportKind).toBe('SUPPORT'); expect(fixture.componentInstance.supportSummary).toContain('Nhờ nhân viên hỗ trợ');
    expect(fixture.nativeElement.querySelector('app-support-request')).not.toBeNull();
  });

  it('opens the real review component for the selected book and replaces it with another panel', () => {
    const reply: ChatMessage = { ...purchase, actions: [
      { type: 'WRITE_REVIEW', label: 'Viết đánh giá', productId: 7, requiresConfirmation: false },
      { type: 'CREATE_SUPPORT_REQUEST', label: 'Soạn yêu cầu', requiresConfirmation: true }
    ] };
    fixture.componentInstance.send('Viết đánh giá cuốn này'); replies.next(reply); replies.complete(); fixture.detectChanges();
    fixture.componentInstance.runAction(reply.actions![0], reply); fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(BookReviewComponent)).componentInstance.productId).toBe(7);
    fixture.componentInstance.runAction(reply.actions![1], reply); fixture.detectChanges();
    expect(fixture.componentInstance.reviewProduct).toBeNull();
    expect(fixture.debugElement.query(By.directive(BookReviewComponent))).toBeNull();
    expect(fixture.componentInstance.supportKind).toBe('SUPPORT');
  });

  it('keeps widget actions blocked until the review save completes', () => {
    auth.currentUser = { userId: 7, email: 'book-review-qa@test.invalid', token: 'fixture' };
    fixture.detectChanges();
    const pending = new Subject<any>(); reviews.upsertReview.and.returnValue(pending);
    const reply: ChatMessage = { ...purchase, actions: [{ type: 'WRITE_REVIEW', label: 'Viết đánh giá', productId: 7, requiresConfirmation: false }] };
    fixture.componentInstance.send('Viết đánh giá cuốn này'); replies.next(reply); replies.complete(); fixture.detectChanges();
    fixture.componentInstance.runAction(reply.actions![0], reply); fixture.detectChanges();
    const review: BookReviewComponent = fixture.debugElement.query(By.directive(BookReviewComponent)).componentInstance;
    review.chooseRating(4); review.content = 'Nhận xét QA'; review.save(); fixture.detectChanges();
    expect(fixture.componentInstance.commerceBusy).toBeTrue();
    const count = fixture.componentInstance.messages.length;
    fixture.componentInstance.send('Câu hỏi khác'); fixture.componentInstance.showCart(); fixture.componentInstance.toggle();
    expect(fixture.componentInstance.messages.length).toBe(count);
    expect(fixture.componentInstance.reviewProduct?.id).toBe(7);
    expect(fixture.componentInstance.open).toBeTrue();
    pending.next({}); pending.complete(); fixture.detectChanges();
    expect(fixture.componentInstance.commerceBusy).toBeFalse();
  });

  it('clears the review panel on a new question, opening the cart, or closing the widget', () => {
    fixture.componentInstance.reviewProduct = purchase.products![0];
    fixture.componentInstance.send('Câu hỏi khác');
    expect(fixture.componentInstance.reviewProduct).toBeNull();
    fixture.componentInstance.reviewProduct = purchase.products![0];
    const showCart = jasmine.createSpy('showCart'); fixture.componentInstance.commerce = { showCart } as any;
    fixture.componentInstance.showCart();
    expect(fixture.componentInstance.reviewProduct).toBeNull(); expect(showCart).toHaveBeenCalled();
    fixture.componentInstance.reviewProduct = purchase.products![0]; fixture.componentInstance.toggle();
    expect(fixture.componentInstance.reviewProduct).toBeNull();
  });

  it('opens a checkout preview only after the completed purchase response', () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    component.send('Mua ngay hai cuốn này');
    replies.next({ ...purchase, streaming:true, execution:{tool:'prepareCheckout',status:'AWAITING_CONFIRMATION'} });
    expect(commerce.runAction).not.toHaveBeenCalled();
    const final: ChatMessage = { ...purchase, streaming:false, execution:{tool:'prepareCheckout',status:'AWAITING_CONFIRMATION'} };
    replies.next(final); replies.next(final);
    expect(commerce.runAction).toHaveBeenCalledTimes(1);
    expect(commerce.runAction).toHaveBeenCalledWith(final.actions![0], final.products![0], '0:');
  });
  it('uses a compact reference for a known book but keeps a new recommendation card', () => {
    const component = fixture.componentInstance;
    const detail: ChatMessage = { ...purchase, intent:'PRODUCT_INFO', execution:{tool:'getProducts',status:'COMPLETED'} };
    component.messages = [purchase,detail]; expect(component.compactProducts(detail)).toBeTrue();
    expect(component.compactProducts({...detail,intent:'DISCOVER_BOOKS'})).toBeFalse();
  });
  it('opens a resolved purchase after a catalog lookup and checkout in the same turn', () => {
    const component = fixture.componentInstance;
    const commerce = fixture.debugElement.query(By.directive(ChatCommerceFixture)).componentInstance as ChatCommerceFixture;
    component.send('Mua hai cuốn sách này');
    replies.next({ ...purchase, streaming:false, execution:{tool:'getProducts,prepareCheckout',status:'AWAITING_CONFIRMATION'} });
    expect(commerce.runAction).toHaveBeenCalledTimes(1);
  });
  it('never opens a review action restored from old conversation history', () => {
    const reply: ChatMessage = { ...purchase, actions: [{ type: 'WRITE_REVIEW', label: 'Viết đánh giá', productId: 7, requiresConfirmation: false }] };
    fixture.componentInstance.messages = [reply]; fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(fixture.componentInstance.reviewProduct).toBeNull();
  });
});

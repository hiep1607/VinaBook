import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
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

describe('Chatbot purchase actions', () => {
  let fixture: ComponentFixture<ChatbotComponent>;
  let replies: Subject<ChatMessage>;
  let originalCache: string | null;
  let originalReviewCache: string | null;
  let auth: any;
  let reviews: any;
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
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule], declarations: [ChatbotComponent, BookReviewComponent], schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ChatbotService, useValue: { useAccount: () => {}, history: () => of([]),
          greeting: () => ({ from: 'bot', text: 'Chào bạn' }), ask: () => replies } },
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

  it('never opens a review action restored from old conversation history', () => {
    const reply: ChatMessage = { ...purchase, actions: [{ type: 'WRITE_REVIEW', label: 'Viết đánh giá', productId: 7, requiresConfirmation: false }] };
    fixture.componentInstance.messages = [reply]; fixture.componentInstance.runAction(reply.actions![0], reply);
    expect(fixture.componentInstance.reviewProduct).toBeNull();
  });
});

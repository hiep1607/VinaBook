import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { Bell, BookOpen, ChevronRight, Clock, Heart, LucideAngularModule, MessageCircle, RefreshCcw, ShieldCheck, ShoppingCart, Tag, Trash2, Truck, X } from 'lucide-angular';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { AutomationPreferences, AutomationService, CustomerNotice } from 'src/app/services/automation.service';
import { ProductService } from 'src/app/services/product.service';
import { RestockService } from 'src/app/services/restock.service';
import { NotificationsComponent } from './notifications.component';

describe('Unified notifications and customer preferences', () => {
  let fixture: ComponentFixture<NotificationsComponent>, page: NotificationsComponent;
  let auth: any, automation: any, stock: any, products: any, refreshed: Subject<void>;
  const notice: CustomerNotice = { id: 'notice1', type: 'PRICE_DROP', title: 'Sách yêu thích đang giảm giá', message: 'Giá sách vừa giảm.', productId: 8, orderId: null, seen: false, link: '/product/8', createdAt: '2026-10-21T10:00:00Z' };
  const waiting = { productId: 7, name: 'Sách đang chờ', state: 'WAITING', seen: false, availableAt: null, inStock: false };
  const preferences: AutomationPreferences = { restock: true, priceDrops: true, orders: true, cartReminders: false, reviewReminders: false, authorReleases: true, emailEnabled: false, emailAvailable: false, followedAuthors: [] };
  beforeEach(async () => {
    auth = { currentUser: { token: 'ownerA', email: 'reader@example.com' } }; refreshed = new Subject();
    automation = {
      unreadCount: 1, refreshed$: refreshed, connect: jasmine.createSpy().and.returnValue(() => {}), syncSession: () => {},
      inbox: jasmine.createSpy().and.returnValue(of({ items: [notice], total: 1, unreadCount: 1 })),
      seen: jasmine.createSpy().and.returnValue(of(undefined)), seenAll: jasmine.createSpy().and.returnValue(of(undefined)), remove: jasmine.createSpy().and.returnValue(of(undefined)),
      preferences: jasmine.createSpy().and.returnValue(of(preferences)), authors: jasmine.createSpy().and.returnValue(of(['Tô Hoài', 'Nguyễn Nhật Ánh'])),
      savePreferences: jasmine.createSpy().and.callFake(value => of(value))
    };
    stock = { list: jasmine.createSpy().and.returnValue(of([waiting])), remove: jasmine.createSpy().and.returnValue(of(undefined)) };
    products = { getProducts: jasmine.createSpy().and.returnValue(of([])) };
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule, RouterTestingModule, LucideAngularModule.pick({ Bell, BookOpen, ChevronRight, Clock, Heart, MessageCircle, RefreshCcw, ShieldCheck, ShoppingCart, Tag, Trash2, Truck, X })],
      declarations: [NotificationsComponent], providers: [{ provide: AuthService, useValue: auth }, { provide: AutomationService, useValue: automation }, { provide: RestockService, useValue: stock }, { provide: ProductService, useValue: products }]
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationsComponent); page = fixture.componentInstance;
  });
  afterEach(() => fixture.destroy());
  const render = () => fixture.detectChanges();

  it('renders the unified inbox and labels the actual notification category', () => {
    render(); expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Thông báo của bạn');
    expect(fixture.nativeElement.querySelector('.notice-row').textContent).toContain('Giảm giá');
    expect(fixture.nativeElement.querySelector('.notice-row h2 a').getAttribute('href')).toMatch(/\/product\/8$/);
  });
  it('routes a cart price change to the cart and exposes its category filter', () => {
    automation.inbox.and.returnValue(of({items:[{...notice,type:'CART_PRICE_CHANGE',title:'Giá sách trong giỏ vừa thay đổi',link:'/product/8'}],total:1,unreadCount:1}));render();
    const row:HTMLElement=fixture.nativeElement.querySelector('.notice-row');expect(row.textContent).toContain('Giá trong giỏ');
    expect(row.querySelector('h2 a')?.getAttribute('href')).toMatch(/\/cart$/);expect(page.actionLabel({...notice,type:'CART_PRICE_CHANGE'})).toBe('Xem giỏ hàng');
    expect(page.kinds.some(kind=>kind.value==='CART_PRICE_CHANGE')).toBeTrue();
  });
  it('saves cart price and wishlist price choices independently', async () => {
    render();page.togglePreferences();render();await fixture.whenStable();render();
    const label=Array.from(fixture.nativeElement.querySelectorAll('.preference-option') as NodeListOf<HTMLLabelElement>).find(item=>item.textContent?.includes('Thay đổi giá sách trong giỏ'))!;
    const cart=label.querySelector('input')!;expect(cart.checked).toBeFalse();cart.click();
    page.preferences!.priceDrops=false;page.preferenceChanged();render();await fixture.whenStable();page.savePreferences();
    expect(automation.savePreferences.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({cartPriceChanges:true,priceDrops:false}));
  });
  it('uses server filtering and paging instead of filtering only the current page', () => {
    render(); page.changeView('unread'); expect(automation.inbox).toHaveBeenCalledWith(0, 'ALL', true);
    page.type = 'PRICE_DROP'; page.changeType(); expect(automation.inbox).toHaveBeenCalledWith(0, 'PRICE_DROP', true);
    page.total = 45; page.changePage(1); expect(automation.inbox).toHaveBeenCalledWith(1, 'PRICE_DROP', true);
  });
  it('ignores untrusted link strings and builds only known internal destinations', () => {
    expect(page.noticeLink({ ...notice, link: 'javascript:alert(1)' })).toBe('/product/8');
    expect(page.noticeLink({ ...notice, type: 'ORDER_STATUS', orderId: 12, link: 'https://evil.example' })).toBe('/orders/12');
    expect(page.noticeLink({ ...notice, type: 'CART_REMINDER', link: '//evil.example' })).toBe('/cart');
    expect(page.noticeLink({ ...notice, productId: -4 })).toBe('/books');
  });
  it('shows loading rather than a false empty result before the response', () => {
    const response = new Subject(); automation.inbox.and.returnValue(response); render();
    expect(fixture.nativeElement.querySelector('.loading-state')).toBeTruthy(); expect(fixture.nativeElement.querySelector('.empty-state')).toBeFalsy();
    response.next({ items: [], total: 0, unreadCount: 0 }); render();
    expect(fixture.nativeElement.querySelector('.empty-state h2').textContent).toContain('Mọi cập nhật');
  });
  it('keeps the last successful inbox visible if refresh fails', () => {
    render(); automation.inbox.and.returnValue(throwError({ status: 0 })); page.load(); render();
    expect(fixture.nativeElement.querySelectorAll('.notice-row').length).toBe(1); expect(page.error).toContain('lần tải gần nhất');
  });
  it('allows initial errors to be retried and does not render an empty success state', () => {
    automation.inbox.and.returnValue(throwError({ status: 500 })); render();
    expect(fixture.nativeElement.querySelector('[role=alert]')).toBeTruthy(); expect(fixture.nativeElement.querySelector('.empty-state')).toBeFalsy();
    automation.inbox.and.returnValue(of({ items: [notice], total: 1, unreadCount: 1 })); page.load(); expect(page.error).toBe(''); expect(page.notices.length).toBe(1);
  });
  it('requires inline confirmation before deleting an inbox item', () => {
    render(); page.remove(notice); expect(automation.remove).not.toHaveBeenCalled();
    page.requestRemoval(notice.id); render(); expect(fixture.nativeElement.querySelector('.remove-confirmation')).toBeTruthy();
    fixture.nativeElement.querySelector('.remove-confirmation .button').click(); expect(page.confirmId).toBe(''); expect(automation.remove).not.toHaveBeenCalled();
  });
  it('prevents repeated clicks from starting overlapping read mutations', () => {
    const response = new Subject<void>(); automation.seen.and.returnValue(response); render();
    page.markRead(notice); page.markRead(notice); expect(automation.seen).toHaveBeenCalledOnceWith('notice1');
    expect(page.actionId).toBe('notice1'); expect(page.notices[0].seen).toBeFalse();
    automation.inbox.and.returnValue(of({ items: [{ ...notice, seen: true }], total: 1, unreadCount: 0 })); response.next();
    expect(page.actionId).toBe(''); expect(page.notices[0].seen).toBeTrue();
  });
  it('keeps the item and an actionable error when deletion fails', () => {
    automation.remove.and.returnValue(throwError({ status: 500 })); render(); page.requestRemoval(notice.id); page.remove(notice);
    expect(page.notices.length).toBe(1); expect(page.error).toContain('Chưa cập nhật'); expect(page.actionId).toBe('');
  });
  it('preserves old restock subscriptions and supports confirmed unfollow', () => {
    render(); page.changeView('waiting'); render();
    expect(fixture.nativeElement.querySelector('.notice-row').textContent).toContain('Sách đang chờ');
    page.removeWatch(waiting); expect(stock.remove).not.toHaveBeenCalled();
    page.requestRemoval('watch-7'); page.removeWatch(waiting); expect(stock.remove).toHaveBeenCalledOnceWith(7); expect(page.watches.length).toBe(0);
  });
  it('clearly distinguishes previously announced books that sold out again', () => {
    stock.list.and.returnValue(of([{ ...waiting, state: 'AVAILABLE', availableAt: notice.createdAt }])); render(); page.changeView('waiting'); render();
    expect(fixture.nativeElement.querySelector('.notice-row').textContent).toContain('Hiện đã hết hàng');
    expect(fixture.nativeElement.querySelector('.notice-row').textContent).toContain('đợt tiếp theo');
  });
  it('allows preparing an email preference while the sender is unavailable', async () => {
    render(); page.togglePreferences(); render(); await fixture.whenStable(); render();
    const email = fixture.nativeElement.querySelector('input[name=emailEnabled]'); expect(email.disabled).toBeFalse(); expect(page.preferences?.emailEnabled).toBeFalse();
    email.click(); render(); await fixture.whenStable(); render();
    expect(fixture.nativeElement.querySelector('#notification-email').value).toBe('reader@example.com');
    expect(fixture.nativeElement.querySelector('.email-service-note').textContent).toContain('lưu email trước');
    page.savePreferences(); expect(automation.savePreferences.calls.mostRecent().args[0].emailAddress).toBe('reader@example.com');
    expect(page.preferenceMessage).toContain('dịch vụ email sẵn sàng');
  });
  it('allows revoking an old email opt-in even when the sender is currently unavailable', async () => {
    automation.preferences.and.returnValue(of({ ...preferences, emailEnabled: true, emailAvailable: false }));
    render(); page.togglePreferences(); render(); await fixture.whenStable(); render();
    const email: HTMLInputElement = fixture.nativeElement.querySelector('input[name=emailEnabled]');
    expect(email.checked).toBeTrue(); expect(email.disabled).toBeFalse();
    email.click(); render(); await fixture.whenStable(); render();
    expect(page.preferences?.emailEnabled).toBeFalse(); expect(email.disabled).toBeFalse();
    page.savePreferences();
    expect(automation.savePreferences.calls.mostRecent().args[0].emailEnabled).toBeFalse();
    expect(page.preferenceDirty).toBeFalse(); expect(page.preferenceMessage).toContain('Đã lưu');
  });
  it('saves a different recipient email and can restore the account suggestion', async () => {
    render(); page.togglePreferences(); page.preferences!.emailEnabled=true; render(); await fixture.whenStable();
    const input:HTMLInputElement=fixture.nativeElement.querySelector('#notification-email');
    input.value='other@example.com';input.dispatchEvent(new Event('input'));render();await fixture.whenStable();
    page.savePreferences();expect(automation.savePreferences.calls.mostRecent().args[0].emailAddress).toBe('other@example.com');
    expect(auth.currentUser.email).toBe('reader@example.com');
    page.useAccountEmail();expect(page.preferences?.emailAddress).toBe('reader@example.com');expect(page.preferenceDirty).toBeTrue();
    expect(fixture.nativeElement.querySelector('datalist option').value).toBe('reader@example.com');
  });
  it('keeps a saved custom recipient instead of overwriting it with the account email', () => {
    automation.preferences.and.returnValue(of({...preferences,emailAddress:'saved@example.com',emailEnabled:true}));
    render();page.togglePreferences();expect(page.preferences?.emailAddress).toBe('saved@example.com');
  });
  it('validates the recipient while allowing opt-out even with an unfinished draft', () => {
    render();page.togglePreferences();page.preferences!.emailEnabled=true;page.preferences!.emailAddress='bad-address';page.preferenceChanged();page.savePreferences();
    expect(automation.savePreferences).not.toHaveBeenCalled();expect(page.preferenceError).toContain('hợp lệ');
    page.preferences!.emailEnabled=false;page.preferenceChanged();page.savePreferences();
    expect(automation.savePreferences.calls.mostRecent().args[0].emailEnabled).toBeFalse();
  });
  it('accepts a manually entered recipient when the account has no suggested email', () => {
    auth.currentUser.email='';render();page.togglePreferences();expect(page.preferences?.emailAddress).toBe('');
    page.preferences!.emailEnabled=true;page.preferences!.emailAddress='manual@example.com';page.preferenceChanged();page.savePreferences();
    expect(automation.savePreferences.calls.mostRecent().args[0].emailAddress).toBe('manual@example.com');
  });
  it('follows only catalog authors, avoids duplicates and persists only after Save', () => {
    render(); page.togglePreferences(); page.author = 'Tô Hoài'; page.followAuthor(); page.author = 'Tô Hoài'; page.followAuthor();
    expect(page.preferences?.followedAuthors).toEqual(['Tô Hoài']); expect(automation.savePreferences).not.toHaveBeenCalled();
    page.author = 'Unknown'; page.followAuthor(); expect(page.preferences?.followedAuthors.length).toBe(1);
    page.savePreferences(); expect(automation.savePreferences).toHaveBeenCalled(); expect(page.preferenceDirty).toBeFalse(); expect(page.preferenceMessage).toContain('Đã lưu');
  });
  it('retains preference edits on save failure for a safe retry', () => {
    automation.savePreferences.and.returnValue(throwError({ status: 500 })); render(); page.togglePreferences();
    page.preferences!.cartReminders = true; page.preferenceChanged(); page.savePreferences();
    expect(page.preferences?.cartReminders).toBeTrue(); expect(page.preferenceDirty).toBeTrue(); expect(page.preferenceBusy).toBeFalse(); expect(page.preferenceError).toContain('Thay đổi của bạn vẫn ở đây');
  });
  it('ignores late account responses and clears private UI state when the owner changes', () => {
    const response = new Subject(); automation.inbox.and.returnValue(response); const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true); render();
    auth.currentUser = { token: 'ownerB' }; response.next({ items: [notice], total: 1, unreadCount: 1 }); expect(page.notices.length).toBe(0);
    page.ngDoCheck(); expect(page.watches.length).toBe(0); expect(page.preferences).toBeNull(); expect(navigate).toHaveBeenCalledWith(['/login']); expect(response.observers.length).toBe(0);
  });
  it('pauses quiet page refresh while the customer is confirming removal', () => {
    render(); const calls = automation.inbox.calls.count(); page.requestRemoval(notice.id); refreshed.next(); expect(automation.inbox.calls.count()).toBe(calls);
    page.confirmId = ''; refreshed.next(); expect(automation.inbox.calls.count()).toBe(calls + 1);
  });
});

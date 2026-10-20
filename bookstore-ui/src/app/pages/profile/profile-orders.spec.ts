import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { LucideAngularModule, BookOpen, ChevronRight, User, ShoppingCart, Bell, Heart } from 'lucide-angular';
import { of, Subject } from 'rxjs';
import { ProfileComponent } from './profile.component';
import { OrderItemsComponent } from '../../components/order-items/order-items.component';
import { AuthService } from '../../services/auth.service';
import { OrderService } from '../../services/order.service';
import { CartService } from '../../services/cart.service';
import { NotificationService } from '../../services/notification.service';
import { AddressService } from '../../services/address.service';
import { ReviewService } from '../../services/review.service';

describe('Profile recent order cards', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let auth: any; let service: any;
  const order = {
    id: 49, date: '2026-10-13', statusCode: 0, status: 'Chờ xử lý', total: 231560,
    items: [
      { productId: 57, name: 'Đắc Nhân Tâm', image: '/cover-test.jpg', quantity: 2, price: 70520 },
      { productId: 58, name: 'Nhà Giả Kim', image: '/cover-test-2.jpg', quantity: 1, price: 63200 },
      { productId: 51, name: 'Doraemon', image: '', quantity: 1, price: 19800 }
    ]
  };
  beforeEach(async () => {
    auth = { currentUser: { name: 'Khách thử', email: 'profile-ui@example.com', token: 'test-owner' }, isLoggedIn: () => true };
    service = { getOrderPage: jasmine.createSpy().and.returnValue(of({items: [order], counts: {'-1': 1}, totalSpent: order.total})) };
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule, RouterModule.forRoot([]), LucideAngularModule.pick({BookOpen, ChevronRight, User, ShoppingCart, Bell, Heart})],
      declarations: [ProfileComponent, OrderItemsComponent],
      providers: [
        {provide: AuthService, useValue: auth}, {provide: OrderService, useValue: service},
        {provide: CartService, useValue: {getCartCount: () => 0}},
        {provide: NotificationService, useValue: {}},
        {provide: AddressService, useValue: {refresh: () => Promise.resolve(), getAddresses: () => []}},
        {provide: ReviewService, useValue: {countByEmail: () => 0}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.componentInstance.activeTab = 'orders'; fixture.detectChanges();
  });

  it('shows saved books and prices, counts copies, and limits a large order to two book previews', () => {
    const card = fixture.nativeElement.querySelector('.recent-order');
    expect(card.querySelectorAll('img').length).toBe(2);
    expect(card.querySelector('img').alt).toBe('Bìa sách Đắc Nhân Tâm');
    expect(card.querySelector('.order-status').textContent).toContain('Chờ xử lý');
    expect(card.querySelector('.book-count').textContent).toContain('4 cuốn · 3 tựa sách');
    expect(card.querySelector('.more-books').textContent).toContain('1 tựa sách khác');
    expect(card.querySelector('.order-detail-link').getAttribute('href')).toBe('/orders/49');
    card.querySelector('img').dispatchEvent(new Event('error')); fixture.detectChanges();
    expect(card.querySelector('[aria-label="Chưa có ảnh bìa"]')).toBeTruthy();
  });

  it('shows loading and an actionable error instead of claiming the customer has no orders', () => {
    const pending = new Subject<any>(); service.getOrderPage.and.returnValue(pending);
    fixture.componentInstance.loadRecentOrders(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Đang tải');
    pending.error(new Error('offline')); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Chưa tải được');
    expect(fixture.nativeElement.textContent).not.toContain('Bạn chưa có đơn hàng nào');
    service.getOrderPage.and.returnValue(of({items: []}));
    fixture.nativeElement.querySelector('.recent-state button').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Bạn chưa có đơn hàng nào');
  });

  it('ignores an older refresh and a response arriving after the account changed', () => {
    const first = new Subject<any>(); service.getOrderPage.and.returnValue(first);
    fixture.componentInstance.loadRecentOrders();
    service.getOrderPage.and.returnValue(of({items: []})); fixture.componentInstance.loadRecentOrders();
    first.next({items: [order]}); expect(fixture.componentInstance.recentOrders.length).toBe(0);
    const other = new Subject<any>(); service.getOrderPage.and.returnValue(other); fixture.componentInstance.loadRecentOrders();
    auth.currentUser.token = 'another-owner'; other.next({items: [order]});
    expect(fixture.componentInstance.recentOrders.length).toBe(0);
  });
});

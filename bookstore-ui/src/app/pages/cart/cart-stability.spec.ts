import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { CartComponent } from './cart.component';
import { CartService } from '../../services/cart.service';
import { NotificationService } from '../../services/notification.service';
import { MotionService } from '../../services/motion.service';
import { StoreKnowledgeService } from '../../services/store-knowledge.service';

describe('Cart quantity rendering stability', () => {
  let fixture: ComponentFixture<CartComponent>;
  let cart: any;
  beforeEach(async () => {
    cart = {
      items: [{ id: 57, name: 'Sách thử', author: 'Tác giả', price: 70520, quantity: 1, stock: 8, selected: true }],
      syncing: false, syncError: '',
      getCartItems() { return this.items; },
      saveCart: jasmine.createSpy(), refresh: jasmine.createSpy()
    };
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule, RouterModule.forRoot([])], declarations: [CartComponent],
      providers: [
        { provide: CartService, useValue: cart },
        { provide: NotificationService, useValue: {} },
        { provide: MotionService, useValue: {} },
        { provide: StoreKnowledgeService, useValue: { get: () => of({settings: {shippingFee: 20000, freeShippingThreshold: 300000}}) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
  });

  it('updates the amount immediately and retains row, cover and focused button after a server acknowledgement', () => {
    const row = fixture.nativeElement.querySelector('.cart-item');
    const cover = row.querySelector('img');
    const increase = row.querySelector('[aria-label="Tăng số lượng Sách thử"]');
    increase.focus(); increase.click(); fixture.detectChanges();
    expect(fixture.componentInstance.getSubtotal()).toBe(141040);
    expect(fixture.nativeElement.querySelector('.summary-row strong').textContent.replace(/\D/g, '')).toBe('141040');
    cart.items.splice(0, 1, { ...cart.items[0] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cart-item')).toBe(row);
    expect(row.querySelector('img')).toBe(cover);
    expect(document.activeElement).toBe(increase);
  });

  it('keeps both columns in place while synchronization starts and finishes', () => {
    const products = fixture.nativeElement.querySelector('.cart-products');
    const summary = fixture.nativeElement.querySelector('.summary-box');
    const positions = () => [products.getBoundingClientRect().top, summary.getBoundingClientRect().top];
    const before = positions();
    cart.syncing = true; fixture.detectChanges();
    expect(positions()).toEqual(before);
    cart.syncing = false; fixture.detectChanges();
    expect(positions()).toEqual(before);
  });

  it('preserves stock limits and blocks changes when server synchronization failed', () => {
    cart.items[0].quantity = 8; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Tăng số lượng Sách thử"]').disabled).toBeTrue();
    cart.syncError = 'Cần tải lại giỏ'; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Giảm số lượng Sách thử"]').disabled).toBeTrue();
    fixture.nativeElement.querySelector('.cart-sync button').click();
    expect(cart.refresh).toHaveBeenCalled();
  });
});

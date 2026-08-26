import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class CartService {

  buyNowItem: any = null;
  items: any[] = [];

  /** Phát ra mỗi khi giỏ đổi (thêm/xóa/cập nhật) — header dùng để bump badge. */
  private changeSource = new Subject<void>();
  readonly changes$ = this.changeSource.asObservable();

  private loadedKey = '';

  constructor(private authService: AuthService) {}

  saveCart(): void {
    this.ensureScope();
    localStorage.setItem(this.loadedKey, JSON.stringify(this.items));
    this.changeSource.next();
  }

  addToCart(product: any, qty: number = 1): void {
    this.ensureScope();
    const existingItem = this.items.find(item => item.id === product.id);

    if (existingItem) {
      const max = product.quantity > 0 ? product.quantity : 99;
      existingItem.quantity = Math.min(existingItem.quantity + qty, max);
    } else {
      this.items.push({ ...product, quantity: qty, selected: true });
    }

    this.saveCart();
  }

  getCartItems(): any[] {
    this.ensureScope();
    return this.items;
  }

  removeItem(item: any): void {
    this.ensureScope();
    this.items = this.items.filter(cartItem => cartItem !== item);
    this.saveCart();
  }

  getCartCount(): number {
    this.ensureScope();
    return this.items.reduce((total, item) => total + item.quantity, 0);
  }

  setBuyNowItem(product: any, qty: number = 1): void {
    this.buyNowItem = { ...product, quantity: qty, selected: true };
  }

  clearBuyNowItem(): void {
    this.buyNowItem = null;
  }

  private ensureScope(): void {
    const email = this.authService.currentUser && this.authService.currentUser.email
      ? this.authService.currentUser.email.toLowerCase()
      : 'guest';
    const key = `cart_${email}`;

    if (key === this.loadedKey) {
      return;
    }

    this.loadedKey = key;
    const scoped = localStorage.getItem(key);

    if (scoped) {
      this.items = this.parseCart(scoped);
      return;
    }

    // Migrate the legacy shared cart once to the first signed-in account that opens it.
    const legacy = localStorage.getItem('cart');
    const migrationOwner = localStorage.getItem('cart_migration_owner');
    if (email !== 'guest' && legacy && (!migrationOwner || migrationOwner === email)) {
      this.items = this.parseCart(legacy);
      localStorage.setItem(key, JSON.stringify(this.items));
      localStorage.setItem('cart_migration_owner', email);
      return;
    }

    this.items = [];
  }

  private parseCart(value: string): any[] {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
}

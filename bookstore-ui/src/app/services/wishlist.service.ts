import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class WishlistService {

  private key(email: string): string {
    return 'wishlist_' + email;
  }

  private save(email: string, ids: number[]): void {
    localStorage.setItem(this.key(email), JSON.stringify(ids));
  }

  getIds(email: string): number[] {
    try {
      const value = JSON.parse(localStorage.getItem(this.key(email)) || '[]');
      return Array.isArray(value) ? [...new Set<number>(value.filter(id => Number.isSafeInteger(id) && id > 0))] : [];
    } catch {
      return [];
    }
  }

  isInWishlist(email: string, productId: number): boolean {
    return this.getIds(email).includes(productId);
  }

  toggleWishlist(email: string, productId: number): boolean {
    const ids = this.getIds(email);
    const idx = ids.indexOf(productId);
    if (idx !== -1) {
      ids.splice(idx, 1);
      this.save(email, ids);
      return false;
    }
    ids.push(productId);
    this.save(email, ids);
    return true;
  }

  addToWishlist(email: string, productId: number): void {
    const ids = this.getIds(email);
    if (!ids.includes(productId)) this.save(email, [...ids, productId]);
  }

  removeFromWishlist(email: string, productId: number): void {
    const ids = this.getIds(email).filter(id => id !== productId);
    this.save(email, ids);
  }

  getCount(email: string): number {
    return this.getIds(email).length;
  }

}

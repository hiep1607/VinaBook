import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { ProductService } from 'src/app/services/product.service';
import { AuthService } from 'src/app/services/auth.service';
import { WishlistService } from 'src/app/services/wishlist.service';
import { ReviewService } from 'src/app/services/review.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-wishlist',
  templateUrl: './wishlist.component.html',
  styleUrls: ['./wishlist.component.scss']
})
export class WishlistComponent implements OnInit {

  private catalog: any[] = [];
  syncing=false;
  async refresh():Promise<void>{if(this.syncing)return;this.syncing=true;try{await this.wishlistService.refresh(this.email);}catch{}finally{this.syncing=false;}}

  constructor(
    public authService: AuthService,
    public wishlistService: WishlistService,
    private reviewService: ReviewService,
    private notification: NotificationService,
    private router: Router,
    private productService: ProductService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.productService.getProducts().subscribe(products => {
      this.catalog = products;
    });
  }

  private get email(): string {
    return this.authService.currentUser?.email || '';
  }

  get wishlistProducts(): any[] {
    const ids = this.wishlistService.getIds(this.email);
    return ids
      .map(id => this.catalog.find(p => p.id === id))
      .filter(p => !!p);
  }

  getAvgRating(productId: number): number {
    return this.reviewService.getAverageRating(productId);
  }

  getReviewCount(productId: number): number {
    return this.reviewService.getReviews(productId).length;
  }

  displayStars(rating: number): string {
    const full = Math.round(rating);
    return '★'.repeat(full) + '☆'.repeat(5 - full);
  }

  async remove(productId: number): Promise<void> {
    try { await this.wishlistService.removeFromWishlist(this.email, productId); this.notification.success('Đã xóa khỏi danh sách yêu thích'); } catch(error) { this.notification.error((error as any)?.error?.message || 'Chưa lưu được thay đổi. Bạn thử lại nhé.'); }
  }

  goToDetail(productId: number): void {
    this.router.navigate(['/product', productId]);
  }

  exploreBooks(): void {
    this.router.navigate(['/books']);
  }

}

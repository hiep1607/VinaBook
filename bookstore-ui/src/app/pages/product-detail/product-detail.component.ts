import {
  Component,
  ElementRef,
  HostListener,
  Inject,
  NgZone,
  OnDestroy,
  OnInit,
  ViewChild,
  AfterViewChecked
} from '@angular/core';
import { Subscription } from 'rxjs';
import { DOCUMENT } from '@angular/common';

import {
  ActivatedRoute,
  Router
} from '@angular/router';

import { ProductService } from 'src/app/services/product.service';

import { CartService } from 'src/app/services/cart.service';
import { ReviewService } from 'src/app/services/review.service';
import { AuthService } from 'src/app/services/auth.service';
import { NotificationService } from 'src/app/services/notification.service';
import { WishlistService } from 'src/app/services/wishlist.service';
import { FlyToCartService } from 'src/app/services/fly-to-cart.service';
import { RecentlyViewedService } from 'src/app/services/recently-viewed.service';

/** Sticky buy bar chỉ dành cho mobile/tablet nhỏ (khớp media query trong SCSS). */
const STICKY_BAR_MAX_WIDTH = 768;

@Component({
  selector: 'app-product-detail',
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.scss']
})
export class ProductDetailComponent implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('relatedSlider') relatedSlider?: ElementRef<HTMLElement>;
  @ViewChild('ctaActions') ctaActions?: ElementRef<HTMLElement>;
  @ViewChild('stickyBar') stickyBarEl?: ElementRef<HTMLElement>;

  product: any;

  reviews: any[] = [];

  relatedBooks: any[] = [];
  loading = true;
  loadError = false;

  private subscriptions = new Subscription();

  // Số lượng mua (stepper)
  qty = 1;

  /** Sticky buy bar đang hiển thị (mobile + CTA gốc rời viewport). */
  stickyBarVisible = false;

  /** Ghi nhớ trạng thái nhìn thấy gần nhất của CTA gốc để tính lại khi resize. */
  private originalCtaVisible = true;

  private stickyObserver?: IntersectionObserver;

  // Phân trang đánh giá
  visibleReviewCount = 5;

  // Form state
  reviewContent = '';
  reviewRating = 0;
  hoverRating = 0;

  // Whether the current user already has a review (edit mode)
  isEditing = false;

  readonly stars = [1, 2, 3, 4, 5];

  constructor(
    private route: ActivatedRoute,
    private cartService: CartService,
    private router: Router,
    private reviewService: ReviewService,
    public authService: AuthService,
    private notification: NotificationService,
    public wishlistService: WishlistService,
    private productService: ProductService,
    private elementRef: ElementRef,
    private flyToCart: FlyToCartService,
    private zone: NgZone,
    private recentlyViewed: RecentlyViewedService,
    @Inject(DOCUMENT) private doc: Document
  ) {}

  ngOnInit(): void {

    // Subscribe paramMap (không dùng snapshot) để bấm "sách cùng thể loại"
    // điều hướng sang sản phẩm khác vẫn nạp lại dữ liệu (component được tái sử dụng)
    this.subscriptions.add(this.route.paramMap.subscribe(params => {

      const id = Number(params.get('id'));

      this.resetState();
      this.loading = true;
      this.loadError = false;

      this.subscriptions.add(this.productService.getProduct(id).subscribe(product => {

        this.product = product;
        this.loading = false;

        if (!this.product) {
          this.router.navigate(['/']);
          return;
        }

        this.loadReviews();
        this.loadUserReview();
        this.loadRelatedBooks();

        // P2: ghi nhận sách đã xem — chỉ sau khi product tải thành công.
        // Component tái sử dụng giữa các route /product/:id nên ID mới
        // được ghi nhận qua chính subscription paramMap hiện có (không trùng).
        this.recentlyViewed.add(id);

        // DOM đã render xong CTA gốc -> gắn IntersectionObserver cho sticky bar.
        setTimeout(() => this.setupStickyObserver());

      }, () => {
        this.loading = false;
        this.loadError = true;
      }));

    }));

    // Cache rates tải bất đồng bộ - đồng bộ lại danh sách review khi sẵn sàng
    this.subscriptions.add(this.reviewService.changes$.subscribe(() => {
      if (this.product) {
        this.loadReviews();
        if (!this.isEditing) {
          this.loadUserReview();
        }
      }
    }));

  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.teardownStickyBar();
  }

  // ---- sticky buy bar (mobile) ----

  /** Resize/đổi hướng: tính lại trạng thái bar từ trạng thái CTA gốc đã biết. */
  @HostListener('window:resize')
  onViewportResize(): void {
    if (this.stickyObserver) {
      this.applyStickyState(this.originalCtaVisible);
    }
  }

  /**
   * Quan sát cụm CTA gốc bằng IntersectionObserver (không polling).
   * Chỉ tạo một lần; khi đổi sang sản phẩm khác, target không đổi nên tái sử dụng.
   */
  private setupStickyObserver(): void {
    const target = this.ctaActions?.nativeElement;

    if (!target || this.stickyObserver || typeof IntersectionObserver === 'undefined') {
      return;
    }

    this.stickyObserver = new IntersectionObserver(entries => {
      const visible = entries.some(entry => entry.isIntersecting);
      // Callback của IO có thể chạy ngoài zone -> chủ động chạy lại CD.
      this.zone.run(() => this.applyStickyState(visible));
    }, { threshold: 0 });

    this.stickyObserver.observe(target);
    this.applyStickyState(true);
  }

  /**
   * Bar chỉ hiện khi: viewport ≤768px + CTA gốc đã rời viewport + có sản phẩm.
   * CTA gốc quay lại thì bar ẩn ngay.
   */
  applyStickyState(originalCtaVisible: boolean): void {
    this.originalCtaVisible = originalCtaVisible;

    const mobileWidth = window.innerWidth <= STICKY_BAR_MAX_WIDTH;
    const next = mobileWidth && !originalCtaVisible && !!this.product && !this.loadError;

    if (next !== this.stickyBarVisible) {
      this.stickyBarVisible = next;
      // Chừa khoảng trống cuối trang để bar không đè footer/review form.
      document.body.classList.toggle('has-sticky-buy-bar', next);
    }
  }

  private teardownStickyBar(): void {
    this.stickyObserver?.disconnect();
    this.stickyObserver = undefined;
    document.body.classList.remove('has-sticky-buy-bar');
    this.stickyBarVisible = false;
  }

  /**
   * Bar phải nằm trực tiếp dưới <body>: host component (và các wrapper route)
   * mang transform từ hiệu ứng chuyển cảnh, khiến position:fixed bị "kẹt"
   * trong ngữ cảnh đó và cuộn theo trang. Portal ra body là cách chắc chắn.
   */
  ngAfterViewChecked(): void {
    const el = this.stickyBarEl?.nativeElement;
    if (el && this.stickyBarVisible && el.parentElement !== this.doc.body) {
      this.doc.body.appendChild(el);
    }
  }

  /** Trạng thái theo từng sản phẩm - phải xóa khi đổi sang sản phẩm khác */
  private resetState(): void {
    this.qty = 1;
    this.visibleReviewCount = 5;
    this.reviewContent = '';
    this.reviewRating = 0;
    this.hoverRating = 0;
    this.isEditing = false;
    this.relatedBooks = [];
    window.scrollTo({ top: 0 });
  }

  private loadRelatedBooks(): void {

    if (!this.product.categoryId) {
      return;
    }

    this.subscriptions.add(this.productService
      .getProductsByCategory(this.product.categoryId)
      .subscribe(list => {
        this.relatedBooks = list
          .filter(p => p.id !== this.product.id)
          .slice(0, 8);
      }));

  }

  loadReviews(): void {
    this.reviews = this.reviewService.getReviews(this.product.id);
  }

  private loadUserReview(): void {

    if (!this.authService.isLoggedIn()) {
      return;
    }

    const existing = this.reviewService.getUserReview(
      this.product.id,
      this.authService.currentUser.email
    );

    if (existing) {
      this.reviewContent = existing.content;
      this.reviewRating  = existing.rating;
      this.isEditing     = true;
    }

  }

  // ---- wishlist ----

  get isWishlisted(): boolean {
    if (!this.authService.isLoggedIn()) {
      return false;
    }
    return this.wishlistService.isInWishlist(
      this.authService.currentUser.email,
      this.product.id
    );
  }

  toggleWishlist(): void {
    if (!this.authService.isLoggedIn()) {
      this.notification.error('Vui lòng đăng nhập để lưu sản phẩm yêu thích');
      this.router.navigate(['/login']);
      return;
    }

    const added = this.wishlistService.toggleWishlist(
      this.authService.currentUser.email,
      this.product.id
    );

    this.notification.success(
      added ? 'Đã thêm vào danh sách yêu thích ♥' : 'Đã xóa khỏi danh sách yêu thích'
    );
  }

  // ---- star picker helpers ----

  setRating(star: number): void {
    this.reviewRating = star;
  }

  setHover(star: number): void {
    this.hoverRating = star;
  }

  clearHover(): void {
    this.hoverRating = 0;
  }

  starClass(star: number): string {

    const active = this.hoverRating
      ? star <= this.hoverRating
      : star <= this.reviewRating;

    return active ? 'star on' : 'star';

  }

  // ---- computed display values ----

  get averageRating(): number {
    return this.reviewService.getAverageRating(this.product.id);
  }

  get reviewCount(): number {
    return this.reviews.length;
  }

  get visibleReviews(): any[] {
    return this.reviews.slice(0, this.visibleReviewCount);
  }

  showMoreReviews(): void {
    this.visibleReviewCount += 5;
  }

  slideRelated(direction: 1 | -1): void {
    const viewport = this.relatedSlider?.nativeElement;
    if (!viewport) {
      return;
    }
    viewport.scrollBy({
      left: direction * Math.max(260, viewport.clientWidth * 0.82),
      behavior: 'smooth'
    });
  }

  get ratingHint(): string {
    const labels = ['', 'Không hài lòng', 'Chưa tốt', 'Bình thường', 'Hài lòng', 'Rất hài lòng'];
    return labels[this.hoverRating || this.reviewRating] || 'Chọn mức đánh giá';
  }

  reviewInitial(name: string): string {
    return (name || 'V').trim().charAt(0).toUpperCase();
  }

  // ---- quantity stepper ----

  /** Trần số lượng = tồn kho backend (product.quantity); không có thì 99 */
  get maxQty(): number {
    if (!this.product || this.product.quantity === undefined || this.product.quantity === null) {
      return 99;
    }
    return Math.max(0, this.product.quantity);
  }

  increaseQty(): void {
    if (this.qty < this.maxQty) {
      this.qty++;
    }
  }

  decreaseQty(): void {
    if (this.qty > 1) {
      this.qty--;
    }
  }

  displayStars(rating: number): string {
    const full  = Math.round(rating);
    return '★'.repeat(full) + '☆'.repeat(5 - full);
  }

  // ---- actions ----

  addReview(): void {

    if (!this.authService.isLoggedIn()) {
      this.notification.error('Vui lòng đăng nhập để đánh giá');
      this.router.navigate(['/login']);
      return;
    }

    if (this.reviewRating === 0) {
      this.notification.error('Vui lòng chọn số sao');
      return;
    }

    if (!this.reviewContent.trim()) {
      this.notification.error('Vui lòng nhập nội dung đánh giá');
      return;
    }

    const user = this.authService.currentUser;
    const wasEditing = this.isEditing;

    this.reviewService
      .upsertReview(this.product.id, {
        userId:  user.userId,
        email:   user.email,
        name:    user.name,
        content: this.reviewContent.trim(),
        rating:  this.reviewRating
      })
      .subscribe(
        () => {

          this.isEditing     = true;
          this.reviewContent = this.reviewContent.trim();

          this.notification.success(
            wasEditing
              ? 'Cập nhật đánh giá thành công'
              : 'Đánh giá đã được gửi'
          );

        },
        () => {
          this.notification.error('Gửi đánh giá thất bại. Vui lòng thử lại sau');
        }
      );

  }

  addToCart(): void {

    if (!this.authService.isLoggedIn()) {
      this.notification.error('Vui lòng đăng nhập trước');
      this.router.navigate(['/login']);
      return;
    }

    if (this.maxQty === 0) {
      this.notification.error('Sản phẩm hiện đã hết hàng');
      return;
    }

    this.cartService.addToCart(this.product, this.qty);
    this.flyToCart.fly(
      (this.elementRef.nativeElement as HTMLElement).querySelector<HTMLImageElement>('.detail-image img')
    );
    this.notification.success('Đã thêm vào giỏ hàng');

  }

  buyNow(): void {

    if (!this.authService.isLoggedIn()) {
      this.notification.error('Vui lòng đăng nhập trước');
      this.router.navigate(['/login']);
      return;
    }

    if (this.maxQty === 0) {
      this.notification.error('Sản phẩm hiện đã hết hàng');
      return;
    }

    this.cartService.setBuyNowItem(this.product, this.qty);
    this.router.navigate(['/checkout']);

  }

}

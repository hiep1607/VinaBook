import { Component, DoCheck, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';
import { AuthService } from 'src/app/services/auth.service';
import { ReviewService } from 'src/app/services/review.service';

@Component({
  selector: 'app-book-review',
  templateUrl: './book-review.component.html',
  styleUrls: ['./book-review.component.scss']
})
export class BookReviewComponent implements OnInit, OnChanges, DoCheck, OnDestroy {
  @Input() productId = 0;
  @Input() productName = 'cuốn sách này';
  @Output() busyChange = new EventEmitter<boolean>();
  @Output() closed = new EventEmitter<void>();

  readonly stars = [1, 2, 3, 4, 5];
  rating = 0;
  content = '';
  editing = false;
  busy = false;
  error = '';
  success = '';
  private touched = false;
  private ownerKey = '';
  private subscriptions = new Subscription();
  private saveRequest?: Subscription;
  private version = 0;
  private destroyed = false;

  constructor(public auth: AuthService, private reviews: ReviewService, private router: Router) {}

  ngOnInit(): void {
    this.captureOwner();
    this.subscriptions.add(this.reviews.changes$.subscribe(() => this.loadExistingReview()));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.productId && !changes.productId.firstChange) {
      this.resetDraft();
      this.loadExistingReview();
    }
  }

  ngDoCheck(): void {
    const nextOwner = this.currentOwner();
    if (nextOwner === this.ownerKey) return;
    this.ownerKey = nextOwner;
    this.resetDraft();
    this.loadExistingReview();
  }

  get remainingCharacters(): number { return Math.max(0, 2000 - this.content.length); }

  chooseRating(value: number): void {
    if (this.busy) return;
    this.rating = value;
    this.touched = true;
    this.error = '';
    this.success = '';
  }

  markContentChanged(): void {
    this.touched = true;
    this.error = '';
    this.success = '';
  }

  save(): void {
    if (this.busy) return;
    if (!this.auth.isLoggedIn()) {
      this.error = 'Bạn đăng nhập để đánh giá được lưu đúng tài khoản nhé.';
      return;
    }
    if (!Number.isInteger(this.productId) || this.productId < 1) {
      this.error = 'Mình chưa xác định được cuốn sách cần đánh giá. Bạn chọn lại sách nhé.';
      return;
    }
    if (!Number.isInteger(this.rating) || this.rating < 1 || this.rating > 5) {
      this.error = 'Bạn chọn từ 1 đến 5 sao để người đọc hiểu cảm nhận của mình nhé.';
      return;
    }
    const content = this.content.trim();
    if (!content) {
      this.error = 'Bạn viết vài dòng cảm nhận để đánh giá có ích với người đọc khác nhé.';
      return;
    }
    if (content.length > 2000) {
      this.error = 'Nhận xét tối đa 2.000 ký tự. Bạn rút gọn một chút rồi gửi nhé.';
      return;
    }

    const user = this.auth.currentUser;
    if (!user || !user.userId || !user.email) {
      this.error = 'Phiên đăng nhập của bạn chưa hợp lệ. Bạn đăng nhập lại rồi gửi đánh giá nhé.';
      return;
    }

    const owner = this.currentOwner();
    const productId = this.productId;
    const version = this.version;
    const wasEditing = this.editing;
    this.busy = true;
    this.busyChange.emit(true);
    this.error = '';
    this.success = '';
    this.saveRequest = this.reviews.upsertReview(productId, {
      userId: user.userId,
      email: user.email,
      name: user.name,
      content,
      rating: this.rating
    }).pipe(timeout(20000), finalize(() => {
      if (!this.current(version, owner, productId)) return;
      this.busy = false;
      this.busyChange.emit(false);
    })).subscribe({
      next: () => {
        if (!this.current(version, owner, productId)) return;
        this.content = content;
        this.editing = true;
        this.touched = false;
        this.success = wasEditing ? 'Mình đã cập nhật đánh giá của bạn.' : 'Mình đã lưu đánh giá của bạn.';
      },
      error: error => {
        if (!this.current(version, owner, productId)) return;
        this.reviews.refresh();
        this.error = error?.status === 401 || error?.status === 403
          ? 'Phiên đăng nhập không còn hợp lệ. Bạn đăng nhập lại để gửi đánh giá nhé.'
          : 'Mình chưa xác nhận được đánh giá đã lưu. Bạn kiểm tra lại rồi thử gửi lần nữa nhé.';
      }
    });
  }

  goToLogin(): void { if (!this.busy) this.router.navigate(['/login']); }

  close(): void { if (!this.busy) this.closed.emit(); }

  private loadExistingReview(): void {
    const user = this.auth.currentUser;
    if (!this.auth.isLoggedIn() || !user?.email || this.productId < 1) {
      this.editing = false;
      return;
    }
    const existing = this.reviews.getUserReview(this.productId, user.email);
    if (existing) {
      this.editing = true;
      if (!this.touched && !this.busy) {
        this.rating = Number(existing.rating) || 0;
        this.content = existing.content || '';
      }
    } else {
      this.editing = false;
    }
  }

  private currentOwner(): string {
    const user = this.auth.currentUser;
    return this.auth.isLoggedIn() && user?.email ? user.email.trim().toLowerCase() : '';
  }

  private captureOwner(): void { this.ownerKey = this.currentOwner(); }

  private current(version: number, owner: string, productId: number): boolean {
    return !this.destroyed && version === this.version && owner === this.currentOwner() && productId === this.productId;
  }

  private resetDraft(): void {
    this.version++;
    this.saveRequest?.unsubscribe();
    if (this.busy) { this.busy = false; this.busyChange.emit(false); }
    this.rating = 0;
    this.content = '';
    this.editing = false;
    this.touched = false;
    this.error = '';
    this.success = '';
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.version++;
    this.saveRequest?.unsubscribe();
    this.subscriptions.unsubscribe();
  }
}

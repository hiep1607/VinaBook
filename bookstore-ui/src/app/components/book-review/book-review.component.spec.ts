import { CommonModule } from '@angular/common';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { ReviewService } from 'src/app/services/review.service';
import { BookReviewComponent } from './book-review.component';

describe('BookReviewComponent', () => {
  let fixture: ComponentFixture<BookReviewComponent>;
  let component: BookReviewComponent;
  let auth: any;
  let reviews: any;
  let router: any;

  beforeEach(async () => {
    auth = { currentUser: { userId: 7, email: 'reader@example.test', name: 'Reader' }, isLoggedIn: () => true };
    reviews = {
      changes$: new BehaviorSubject<any>(undefined),
      getUserReview: jasmine.createSpy('getUserReview').and.returnValue(null),
      upsertReview: jasmine.createSpy('upsertReview').and.returnValue(of({})),
      refresh: jasmine.createSpy('refresh')
    };
    router = { navigate: jasmine.createSpy('navigate') };
    await TestBed.configureTestingModule({
      declarations: [BookReviewComponent], imports: [CommonModule, FormsModule],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ReviewService, useValue: reviews },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BookReviewComponent);
    component = fixture.componentInstance;
    component.productId = 9;
    component.productName = 'Sách QA';
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('requires stars and useful comment text before submitting', () => {
    component.save();
    expect(component.error).toContain('chọn từ 1 đến 5 sao');
    expect(reviews.upsertReview).not.toHaveBeenCalled();
    component.chooseRating(5);
    component.content = '   ';
    component.save();
    expect(component.error).toContain('vài dòng cảm nhận');
    expect(reviews.upsertReview).not.toHaveBeenCalled();
  });

  it('sends the selected book and authenticated reviewer to the shared review service', () => {
    component.chooseRating(4);
    component.content = '  Nội dung dễ đọc.  ';
    component.markContentChanged();
    component.save();

    expect(reviews.upsertReview).toHaveBeenCalledOnceWith(9, {
      userId: 7, email: 'reader@example.test', name: 'Reader', content: 'Nội dung dễ đọc.', rating: 4
    });
    expect(component.success).toContain('đã lưu đánh giá');
    expect(component.editing).toBeTrue();
  });

  it('loads and updates the current reviewer’s existing review without creating a second one', () => {
    reviews.getUserReview.and.returnValue({ rating: 3, content: 'Bản cũ' });
    reviews.changes$.next(undefined);
    component.chooseRating(5);
    component.content = 'Bản mới';
    component.markContentChanged();
    component.save();

    expect(component.rating).toBe(5);
    expect(component.content).toBe('Bản mới');
    expect(reviews.upsertReview).toHaveBeenCalledOnceWith(9, jasmine.objectContaining({ rating: 5, content: 'Bản mới' }));
    expect(component.success).toContain('cập nhật');
  });

  it('does not submit twice while the first request is pending', () => {
    const pending = new Subject<any>();
    reviews.upsertReview.and.returnValue(pending);
    component.chooseRating(5);
    component.content = 'Đánh giá QA';
    component.save();
    component.save();

    expect(reviews.upsertReview).toHaveBeenCalledTimes(1);
    expect(component.busy).toBeTrue();
    pending.next({});
    pending.complete();
    expect(component.busy).toBeFalse();
    expect(component.success).toContain('đã lưu');
  });

  it('refreshes before allowing a retry after an uncertain response', () => {
    reviews.upsertReview.and.returnValue(throwError({ status: 0 }));
    component.chooseRating(5);
    component.content = 'Đánh giá QA';
    component.save();

    expect(reviews.refresh).toHaveBeenCalled();
    expect(component.error).toContain('kiểm tra lại');
    expect(component.success).toBe('');
    expect(component.busy).toBeFalse();
  });

  it('clears the previous account’s draft when the signed-in user changes', () => {
    component.chooseRating(5);
    component.content = 'Bản nháp của A';
    auth.currentUser = { userId: 8, email: 'other@example.test', name: 'Other' };
    fixture.detectChanges();

    expect(component.rating).toBe(0);
    expect(component.content).toBe('');
    expect(component.editing).toBeFalse();
  });

  it('prompts a guest to sign in and never sends the review', () => {
    auth.currentUser = null;
    auth.isLoggedIn = () => false;
    fixture.detectChanges();
    component.chooseRating(5);
    component.content = 'Không được gửi khi chưa đăng nhập';
    component.save();

    expect(fixture.nativeElement.textContent).toContain('Đăng nhập');
    expect(reviews.upsertReview).not.toHaveBeenCalled();
    component.goToLogin();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('stops waiting after twenty seconds and lets the customer recover', fakeAsync(() => {
    const pending = new Subject<any>();
    reviews.upsertReview.and.returnValue(pending);
    component.chooseRating(5); component.content = 'Nhận xét QA';
    component.save(); tick(20001);
    expect(component.busy).toBeFalse();
    expect(component.error).toContain('chưa xác nhận được');
    expect(reviews.refresh).toHaveBeenCalled();
    expect(pending.observers.length).toBe(0);
  }));

  it('never copies a late response from one book into another book’s draft', () => {
    const pending = new Subject<any>();
    reviews.upsertReview.and.returnValue(pending);
    component.chooseRating(5); component.content = 'Nhận xét cuốn A'; component.save();
    component.productId = 10;
    component.ngOnChanges({ productId: new SimpleChange(9, 10, false) });
    pending.next({}); pending.complete();
    expect(component.success).toBe('');
    expect(component.content).toBe('');
    expect(component.rating).toBe(0);
    expect(component.busy).toBeFalse();
    expect(pending.observers.length).toBe(0);
  });

  it('ignores an in-flight result after switching accounts', () => {
    const pending = new Subject<any>();
    reviews.upsertReview.and.returnValue(pending);
    component.chooseRating(4); component.content = 'Nhận xét của A'; component.save();
    auth.currentUser = { userId: 8, email: 'other@example.test', name: 'Other' };
    fixture.detectChanges(); pending.next({}); pending.complete();
    expect(component.content).toBe(''); expect(component.success).toBe('');
    expect(component.busy).toBeFalse();
  });

  it('cancels its active subscription when removed from the widget', () => {
    const pending = new Subject<any>();
    reviews.upsertReview.and.returnValue(pending);
    component.chooseRating(5); component.content = 'Nhận xét QA'; component.save();
    component.ngOnDestroy();
    expect(pending.observers.length).toBe(0);
    pending.next({}); expect(component.success).toBe('');
  });

  it('rejects text over the displayed character limit', () => {
    component.chooseRating(5); component.content = 'a'.repeat(2001); component.save();
    expect(component.error).toContain('2.000 ký tự');
    expect(reviews.upsertReview).not.toHaveBeenCalled();
  });
});

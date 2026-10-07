import { HttpClientTestingModule, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from 'src/environments/environment';
import { ReviewService } from './review.service';

describe('Shared review data', () => {
  let service: ReviewService;
  let http: HttpTestingController;
  let initial: TestRequest;
  const url = environment.apiUrl + '/api/rates';
  const input = { userId: 7, email: 'reader@example.test', name: 'Reader', content: 'Dễ đọc.', rating: 4 };
  const rate = (rating = 4, comment = 'Dễ đọc.', email = input.email) =>
    ({ id: 11, rating, comment, product: { productId: 9 }, user: { userId: 7, email, name: 'Reader' } });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ReviewService); http = TestBed.inject(HttpTestingController);
    initial = http.expectOne(url);
  });
  afterEach(() => http.verify());

  it('includes old fractional ratings and star-only reviews in the shared average', () => {
    initial.flush([rate(4.5, ''), { ...rate(1.5, 'Bản in khó đọc.'), id: 12 }, { ...rate(0), id: 13 }]);
    expect(service.getReviews(9).length).toBe(2);
    expect(service.getAverageRating(9)).toBe(3);
  });

  it('updates web consumers immediately from a confirmed write', () => {
    initial.flush([]);
    service.upsertReview(9, input).subscribe();
    const post = http.expectOne(url); expect(post.request.method).toBe('POST'); post.flush(rate());
    expect(service.getUserReview(9, input.email).content).toBe(input.content);
    expect(service.getAverageRating(9)).toBe(4);
    http.expectOne(url).flush([rate()]);
  });

  it('does not let an older refresh overwrite a saved review', () => {
    service.upsertReview(9, input).subscribe();
    const post = http.expectOne(request => request.url === url && request.method === 'POST'); post.flush(rate());
    const refresh = http.expectOne(url);
    initial.flush([rate(1, 'Bản cũ')]);
    expect(service.getUserReview(9, input.email).rating).toBe(4);
    refresh.flush([rate()]);
  });

  it('recognizes the same reviewer regardless of email casing', () => {
    initial.flush([rate(3, 'Bản cũ', 'READER@EXAMPLE.TEST')]);
    expect(service.getUserReview(9, input.email)).not.toBeNull();
    service.upsertReview(9, input).subscribe();
    const put = http.expectOne(url);
    expect(put.request.method).toBe('PUT'); expect(put.request.body.id).toBe(11); put.flush(rate());
    http.expectOne(url).flush([rate()]);
  });

  it('retains confirmed data when the follow-up refresh fails', () => {
    initial.flush([rate()]); service.refresh();
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(service.getAverageRating(9)).toBe(4);
  });
});

import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';

import { environment } from '../../environments/environment';
import { UploadService } from './upload.service';

describe('UploadService', () => {
  let service: UploadService;
  let httpMock: HttpTestingController;
  const configuredUrl = environment.cloudinaryUploadUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [UploadService]
    });
    service = TestBed.inject(UploadService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Khôi phục giá trị môi trường phòng khi test trước đó có ghi đè.
    (environment as any).cloudinaryUploadUrl = configuredUrl;
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('posts the product image to the configured Cloudinary URL', () => {
    if (!configuredUrl) {
      pending('cloudinaryUploadUrl is not configured in this environment');
      return;
    }
    const file = new File(['fake-image'], 'product.png', { type: 'image/png' });

    service.uploadProduct(file).subscribe();

    const req = httpMock.expectOne(configuredUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.get('file')).toBe(file);
    expect(req.request.body.get('upload_preset')).toBe('product-image');
    req.flush({});
  });

  it('returns a clear error when the Cloudinary upload URL is not configured', () => {
    const original = (environment as any).cloudinaryUploadUrl;
    try {
      (environment as any).cloudinaryUploadUrl = '   ';

      let errorMessage = '';
      let gotValue = false;

      service
        .uploadCustomer(new File(['fake-image'], 'customer.png'))
        .subscribe({
          next: () => { gotValue = true; },
          error: (err: Error) => { errorMessage = err.message; }
        });

      expect(gotValue).toBeFalse();
      expect(errorMessage).toBe('Cloudinary upload URL is not configured.');
      // Không được phép phát sinh HTTP request khi config rỗng.
      httpMock.expectNone(() => true);
    } finally {
      (environment as any).cloudinaryUploadUrl = original;
    }
  });
});

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UploadService {

  constructor(private http: HttpClient) { }

  uploadProduct(file: File): Observable<any> {
    return this.upload(file, 'product-image');
  }

  uploadCustomer(file: File): Observable<any> {
    return this.upload(file, 'user-image');
  }

  /**
   * Upload lên Cloudinary bằng unsigned preset (không bao giờ nhúng API secret
   * vào Angular). URL lấy từ environment; khi chưa cấu hình thì trả về lỗi
   * qua Observable (không throw đồng bộ) để caller xử lý khi subscribe.
   */
  private upload(file: File, uploadPreset: string): Observable<any> {
    const url = (environment.cloudinaryUploadUrl || '').trim();
    if (!url) {
      return throwError(new Error('Cloudinary upload URL is not configured.'));
    }
    const data = new FormData();
    data.append('file', file);
    data.append('upload_preset', uploadPreset);
    return this.http.post(url, data);
  }
}

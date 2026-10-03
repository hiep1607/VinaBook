import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { shareReplay } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

export interface StoreSettings {
  shippingFee: number;
  freeShippingThreshold: number;
  deliveryMinDays: number;
  deliveryMaxDays: number;
}
export interface StorePolicy {
  policyKey: string;
  title: string;
  body: string;
  effectiveAt: string;
}
export interface StoreKnowledge {
  settings: StoreSettings;
  policies: StorePolicy[];
  paymentMethods: { [key: string]: { label: string; available: boolean } };
}

@Injectable({ providedIn: 'root' })
export class StoreKnowledgeService {
  private snapshot?: Observable<StoreKnowledge>;
  constructor(private http: HttpClient) { }
  get(): Observable<StoreKnowledge> {
    if (!this.snapshot) {
      this.snapshot = this.http.get<StoreKnowledge>(`${environment.apiUrl}/api/store-knowledge`).pipe(shareReplay(1));
    }
    return this.snapshot;
  }
  getFresh(): Observable<StoreKnowledge> {
    this.snapshot = undefined;
    return this.get();
  }
  static shipping(subtotal: number, settings: StoreSettings): number {
    return subtotal > 0 && subtotal < settings.freeShippingThreshold ? settings.shippingFee : 0;
  }
  static shippingText(settings: StoreSettings): string {
    return `Phí giao hàng ${settings.shippingFee.toLocaleString('vi-VN')}đ, miễn phí khi tiền sách từ ${settings.freeShippingThreshold.toLocaleString('vi-VN')}đ. Thời gian giao dự kiến ${settings.deliveryMinDays}–${settings.deliveryMaxDays} ngày làm việc.`;
  }
}

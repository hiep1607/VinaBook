import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
export interface SupportDraft { requestKey: string; kind: 'RETURN' | 'SUPPORT'; orderId?: number; productId?: number; receivedOn?: string; outcome?: string; message: string; confirmed: boolean; }
export interface SupportRequest extends SupportDraft { id: number; status: string; reply: string; createdAt: string; updatedAt: string; version: number; }
@Injectable({ providedIn: 'root' })
export class SupportRequestService {
  private api = environment.apiUrl + '/api/support/me';
  constructor(private http: HttpClient) {}
  mine() { return this.http.get<SupportRequest[]>(this.api).pipe(timeout(20000)); }
  submit(draft: SupportDraft) { return this.http.post<SupportRequest>(this.api, draft).pipe(timeout(20000)); }
  recover(key: string) { return this.http.get<SupportRequest>(this.api + '/request/' + encodeURIComponent(key)).pipe(timeout(20000)); }
  returnOptions(orderId: number) { return this.http.get<{orderId: number; items: {productId: number; name: string; quantity: number}[]}>(this.api + '/order/' + orderId).pipe(timeout(20000)); }
  statusLabel(status: string): string { return ({ OPEN: 'Đã tiếp nhận', IN_REVIEW: 'Đang xem xét', RESOLVED: 'Đã phản hồi, hoàn tất', REJECTED: 'Không chấp nhận yêu cầu' } as {[key: string]: string})[status] || 'Chưa rõ'; }
}

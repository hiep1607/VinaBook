import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { PageService } from '../../services/page.service';
import { timeout, takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';
interface Request { id: number; kind: string; orderId?: number; productId?: number; message: string; receivedOn?: string; outcome?: string; status: string; reply: string; createdAt: string; version: number; draftStatus?: string; draftReply?: string; }
@Component({ selector: 'app-support-requests', templateUrl: './support-requests.component.html', styleUrls: ['../customer-workspace.css', './support-requests.component.css'] })
export class SupportRequestsComponent implements OnInit, OnDestroy {
  chatId = 0; selectedId = 0; refundOpen = false;
  requests: Request[] = []; status = 'OPEN'; page = 0; hasNext = false; busy = false; error = ''; saving = 0; success = '';
  private readonly api = environment.apiUrl + '/api/support/admin';
  private readonly destroy$ = new Subject<void>();
  constructor(private http: HttpClient, private pages: PageService) {}
  ngOnInit(): void { this.pages.setPageActive('support-requests'); this.load(); }
  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
  get selectedRequest(): Request | undefined { return this.requests.find(request => request.id === this.selectedId) || this.requests[0]; }
  statusLabel(status: string): string { return ({ OPEN: 'Mới tiếp nhận', IN_REVIEW: 'Đang xem xét', RESOLVED: 'Đã hoàn tất', REJECTED: 'Không chấp nhận' } as {[key:string]:string})[status] || status; }
  statusClass(status: string): string { return 'ws-badge-' + status.toLowerCase().replace('_', '-'); }
  track(_: number, request: Request): number { return request.id; }
  select(request: Request): void {
    if (this.saving || this.busy || this.selectedRequest?.id === request.id) { return; }
    this.selectedId = request.id; this.chatId = 0; this.refundOpen = false; this.success = '';
  }
  changeStatus(): void { this.page = 0; this.load(); }
  changePage(step: number): void { if (this.busy || this.saving || (step < 0 && !this.page) || (step > 0 && !this.hasNext)) { return; } this.page += step; this.load(); }
  load(): void {
    if (this.busy || this.saving) { return; }
    this.busy = true; this.error = ''; this.success = '';
    this.http.get<{items: Request[]; page: number; hasNext: boolean}>(this.api, { params: { status: this.status, page: String(this.page) } }).pipe(timeout(20000), takeUntil(this.destroy$)).subscribe({
      next: result => {
        this.requests = result.items.map(request => ({ ...request, draftStatus: request.status === 'OPEN' ? 'IN_REVIEW' : request.status, draftReply: request.reply }));
        this.hasNext = result.hasNext; this.selectedId = this.selectedRequest?.id || 0; this.chatId = 0; this.refundOpen = false; this.busy = false;
      },
      error: error => { this.busy = false; this.error = error?.error?.message || 'Không tải được yêu cầu. Bạn thử lại nhé.'; }
    });
  }
  save(request: Request): void {
    if (this.busy || this.saving || !request.draftReply?.trim()) { return; }
    this.saving = request.id; this.error = ''; this.success = '';
    this.http.put<Request>(this.api + '/' + request.id, { status: request.draftStatus, reply: request.draftReply.trim(), version: request.version }).pipe(timeout(20000), takeUntil(this.destroy$)).subscribe({
      next: result => { Object.assign(request, result); request.draftStatus = result.status; request.draftReply = result.reply; this.saving = 0; this.success = 'Đã lưu phản hồi cho yêu cầu #' + request.id + '.'; },
      error: error => { this.saving = 0; this.error = error?.error?.message || 'Chưa xác nhận được kết quả lưu. Tải lại yêu cầu trước khi gửi lại phản hồi.'; }
    });
  }
}

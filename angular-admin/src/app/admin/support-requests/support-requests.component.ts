import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { PageService } from '../../services/page.service';
import { timeout } from 'rxjs/operators';
interface Request { id: number; kind: string; orderId?: number; productId?: number; message: string; receivedOn?: string; outcome?: string; status: string; reply: string; createdAt: string; version: number; draftStatus?: string; draftReply?: string; }
@Component({ selector: 'app-support-requests', templateUrl: './support-requests.component.html', styleUrls: ['./support-requests.component.css'] })
export class SupportRequestsComponent implements OnInit {
  requests: Request[] = []; status = 'OPEN'; page = 0; hasNext = false; busy = false; error = ''; saving = 0;
  private api = environment.apiUrl + '/api/support/admin';
  constructor(private http: HttpClient, private pages: PageService) {}
  ngOnInit(): void { this.pages.setPageActive('support-requests'); this.load(); }
  load(): void {
    if (this.busy || this.saving) return; this.busy = true; this.error = '';
    this.http.get<{items: Request[]; page: number; hasNext: boolean}>(this.api, { params: { status: this.status, page: String(this.page) } }).pipe(timeout(20000)).subscribe({
      next: result => { this.requests = result.items.map(request => ({ ...request, draftStatus: request.status === 'OPEN' ? 'IN_REVIEW' : request.status, draftReply: request.reply })); this.hasNext = result.hasNext; this.busy = false; },
      error: error => { this.busy = false; this.error = error?.error?.message || 'Không tải được yêu cầu. Vui lòng thử lại.'; }
    });
  }
  save(request: Request): void {
    if (this.busy || this.saving || !request.draftReply?.trim()) return; this.saving = request.id; this.error = '';
    this.http.put<Request>(this.api + '/' + request.id, { status: request.draftStatus, reply: request.draftReply.trim(), version: request.version }).pipe(timeout(20000)).subscribe({
      next: result => { Object.assign(request, result); this.saving = 0; },
      error: error => { this.saving = 0; this.error = error?.error?.message || 'Chưa xác nhận được kết quả lưu. Tải lại yêu cầu trước khi gửi lại phản hồi.'; }
    });
  }
}

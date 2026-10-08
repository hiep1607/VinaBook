import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { PageService } from '../../services/page.service';

interface Voucher {
  id?: number; code: string; kind: string; value: number; maximumDiscount: number;
  minimumSubtotal: number; usageLimit: number; perUserLimit: number; active: boolean;
  startsAt?: string; expiresAt?: string;
}

@Component({ selector: 'app-vouchers', templateUrl: './vouchers.component.html', styleUrls: ['../customer-workspace.css', './vouchers.component.css'] })
export class VouchersComponent implements OnInit, OnDestroy {
  rows: Voucher[] = [];
  draft: Voucher = this.empty();
  start = ''; end = ''; busy = false; saving = false; error = ''; saveError = ''; success = '';
  query = ''; stateFilter = ''; loaded = false;
  private request?: Subscription;
  private readonly api = environment.apiUrl + '/api/vouchers/admin';

  constructor(private http: HttpClient, private pages: PageService) {}

  empty(): Voucher {
    return { code: '', kind: 'FIXED', value: 10000, maximumDiscount: 0, minimumSubtotal: 100000, usageLimit: 100, perUserLimit: 1, active: true };
  }
  ngOnInit(): void { this.pages.setPageActive('vouchers'); this.load(); }
  ngOnDestroy(): void { this.request?.unsubscribe(); }

  get filteredRows(): Voucher[] {
    const query = this.query.trim().toLowerCase();
    return this.rows.filter(row => (!query || row.code.toLowerCase().includes(query)) && (!this.stateFilter || this.state(row) === this.stateFilter));
  }
  state(row: Voucher): string {
    if (!row.active) { return 'disabled'; }
    const now = Date.now();
    if (row.expiresAt && new Date(row.expiresAt).getTime() <= now) { return 'expired'; }
    if (row.startsAt && new Date(row.startsAt).getTime() > now) { return 'scheduled'; }
    return 'active';
  }
  stateLabel(row: Voucher): string {
    return ({ active: 'Đang áp dụng', disabled: 'Đã tắt', expired: 'Hết hạn', scheduled: 'Sắp bắt đầu' } as {[key: string]: string})[this.state(row)];
  }
  track(_: number, row: Voucher): number | string { return row.id || row.code; }

  load(): void {
    if (this.busy || this.saving) { return; }
    this.busy = true; this.error = '';
    this.request = this.http.get<Voucher[]>(this.api).pipe(timeout(15000)).subscribe({
      next: rows => { this.rows = rows; this.loaded = true; this.busy = false; },
      error: e => { this.busy = false; this.error = e?.error?.message || 'Chưa tải được mã giảm giá. Bạn thử lại nhé.'; }
    });
  }
  edit(row?: Voucher): void {
    if (this.busy || this.saving) { return; }
    this.draft = row ? {...row} : this.empty();
    this.start = this.localDate(row?.startsAt); this.end = this.localDate(row?.expiresAt);
    this.saveError = ''; this.success = '';
  }
  private localDate(value?: string): string {
    if (!value) { return ''; }
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) { return ''; }
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }
  save(): void {
    if (this.busy || this.saving) { return; }
    const start = new Date(this.start), end = new Date(this.end);
    if (!this.start || !this.end || !Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
      this.saveError = 'Bạn chọn thời gian bắt đầu và kết thúc hợp lệ nhé.'; return;
    }
    if (this.draft.perUserLimit > this.draft.usageLimit) {
      this.saveError = 'Lượt mỗi tài khoản không được lớn hơn tổng lượt sử dụng.'; return;
    }
    this.saving = true; this.saveError = ''; this.success = '';
    this.request = this.http.put(this.api, {...this.draft, startsAt: start.toISOString(), expiresAt: end.toISOString()}).pipe(timeout(15000)).subscribe({
      next: () => { this.saving = false; this.edit(); this.success = 'Đã lưu mã giảm giá.'; this.load(); },
      error: e => { this.saving = false; this.saveError = e?.error?.message || 'Chưa lưu được mã giảm giá. Nội dung bạn nhập vẫn được giữ lại.'; }
    });
  }
}

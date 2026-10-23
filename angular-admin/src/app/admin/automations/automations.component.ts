import { Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AutomationAdminService, AutomationDashboard, AutomationRule, AutomationRuleKey, AutomationRun, CatalogDemandReport } from '../../services/automation-admin.service';
import { PageService } from '../../services/page.service';
import { SessionService } from '../../services/session.service';

@Component({
  selector: 'app-automations',
  templateUrl: './automations.component.html',
  styleUrls: ['./automations.component.css']
})
export class AutomationsComponent implements OnInit, OnDestroy, DoCheck {
  data?: AutomationDashboard;
  rules: AutomationRule[] = [];
  loading = false;
  running = false;
  savingKey = '';
  error = '';
  success = '';
  ruleErrors: { [key: string]: string } = {};
  updatedAt: Date | null = null;
  catalogDemand?: CatalogDemandReport;
  demandLoading = false;
  demandError = '';
  private saved: { [key: string]: AutomationRule } = {};
  private owner = '';
  private ended = false;
  private requests = new Subscription();

  constructor(
    private automations: AutomationAdminService,
    private pages: PageService,
    private session: SessionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.pages.setPageActive('automations');
    this.owner = this.session.getToken() || '';
    if (!this.current()) { this.leaveSession(); return; }
    this.load();
    this.loadCatalogDemand();
  }
  ngOnDestroy(): void { this.ended = true; this.requests.unsubscribe(); this.owner = ''; }
  ngDoCheck(): void {
    if (!this.ended && this.owner && !this.current()) { this.leaveSession(); }
  }
  private current(): boolean {
    return !this.ended && !!this.owner && this.owner === this.session.getToken() && this.session.isAdmin() && !!this.session.getUser();
  }
  private leaveSession(): void {
    this.data = undefined;
    this.rules = [];
    this.saved = {};
    this.ruleErrors = {};
    this.error = '';
    this.success = '';
    this.loading = false;
    this.running = false;
    this.savingKey = '';
    this.updatedAt = null;
    this.catalogDemand = undefined;
    this.demandLoading = false;
    this.demandError = '';
    this.ended = true;
    this.requests.unsubscribe();
    this.router.navigate(['/login']);
  }
  private accept(data: AutomationDashboard): void {
    this.data = data;
    this.rules = data.rules.map(rule => ({ ...rule }));
    this.saved = {};
    this.rules.forEach(rule => this.saved[rule.key] = { ...rule });
    this.ruleErrors = {};
    this.updatedAt = new Date();
  }
  private fail(error: any, fallback: string): void {
    if (!this.current()) { return; }
    if (error?.status === 401 || error?.status === 403) { this.leaveSession(); return; }
    this.error = fallback;
  }
  get busy(): boolean { return this.loading || this.running || !!this.savingKey; }
  get hasUnsavedChanges(): boolean { return this.rules.some(rule => this.dirty(rule)); }
  dirty(rule: AutomationRule): boolean {
    const saved = this.saved[rule.key];
    return !!saved && (rule.enabled !== saved.enabled || rule.delayHours !== saved.delayHours || rule.threshold !== saved.threshold);
  }
  hasDelay(rule: AutomationRule): boolean { return rule.key === 'CART_REMINDER' || rule.key === 'REVIEW_REMINDER'; }
  icon(key: AutomationRuleKey): string {
    return ({ RESTOCK: 'fa-bell', PRICE_DROP: 'fa-tags', CART_PRICE_CHANGE: 'fa-shopping-cart', ORDER_STATUS: 'fa-truck', CART_REMINDER: 'fa-shopping-cart', REVIEW_REMINDER: 'fa-star', AUTHOR_RELEASE: 'fa-book-open', LOW_STOCK: 'fa-boxes' })[key];
  }
  trackRule(_index: number, rule: AutomationRule): string { return rule.key; }
  trackProduct(_index: number, row: { productId: number }): number { return row.productId; }
  trackRun(_index: number, run: AutomationRun): string { return run.id; }
  trackTitle(_index: number, row: { title: string }): string { return row.title; }
  runStatus(run: AutomationRun): string {
    return run.status === 'FAILED' || run.status === 'ERROR' ? 'Có lỗi' : run.status === 'RUNNING' ? 'Đang kiểm tra' : 'Hoàn tất';
  }

  load(): void {
    if (this.busy || this.hasUnsavedChanges || !this.current()) { return; }
    this.loading = true;
    this.error = '';
    this.success = '';
    this.requests.add(this.automations.get().subscribe({
      next: data => { if (!this.current()) { return; } this.accept(data); this.loading = false; },
      error: error => { if (!this.current()) { return; } this.loading = false; this.fail(error, this.data ? 'Chưa cập nhật được dữ liệu. Thông tin bên dưới là lần tải gần nhất.' : 'Chưa tải được tự động hóa. Bạn kiểm tra kết nối rồi thử lại nhé.'); }
    }));
  }
  restore(rule: AutomationRule): void {
    if (this.busy || !this.saved[rule.key]) { return; }
    Object.assign(rule, this.saved[rule.key]);
    delete this.ruleErrors[rule.key];
  }
  save(rule: AutomationRule): void {
    if (this.busy || !this.current() || !this.dirty(rule)) { return; }
    if (this.hasDelay(rule) && (!Number.isInteger(rule.delayHours) || Number(rule.delayHours) < 1 || Number(rule.delayHours) > 720)) {
      this.ruleErrors[rule.key] = 'Nhập số giờ nguyên từ 1 đến 720.'; return;
    }
    if (rule.key === 'LOW_STOCK' && (!Number.isInteger(rule.threshold) || Number(rule.threshold) < 0 || Number(rule.threshold) > 10000)) {
      this.ruleErrors[rule.key] = 'Nhập ngưỡng tồn kho nguyên từ 0 đến 10.000.'; return;
    }
    this.savingKey = rule.key;
    this.error = '';
    this.success = '';
    delete this.ruleErrors[rule.key];
    const snapshot = { ...rule };
    this.requests.add(this.automations.save(snapshot).subscribe({
      next: saved => {
        if (!this.current()) { return; }
        Object.assign(rule, saved);
        this.saved[rule.key] = { ...saved };
        if (this.data) { this.data.rules = this.data.rules.map(item => item.key === saved.key ? { ...saved } : item); }
        this.savingKey = '';
        this.success = 'Đã lưu quy tắc “' + saved.label + '”.';
      },
      error: error => {
        if (!this.current()) { return; }
        this.savingKey = '';
        if (error?.status === 401 || error?.status === 403) { this.leaveSession(); return; }
        this.ruleErrors[rule.key] = 'Chưa lưu được quy tắc. Thay đổi của bạn vẫn được giữ lại để thử lại.';
      }
    }));
  }
  run(): void {
    if (this.busy || !this.data || this.hasUnsavedChanges || !this.current()) { return; }
    this.running = true;
    this.error = '';
    this.success = '';
    this.requests.add(this.automations.run().subscribe({
      next: data => {
        if (!this.current()) { return; }
        this.accept(data);
        this.running = false;
        const latest = data.recentRuns[0];
        if (latest && (latest.status === 'FAILED' || latest.status === 'ERROR')) {
          this.error = latest.error || 'Lần kiểm tra chưa hoàn tất. Bạn xem lịch sử và thử lại sau nhé.';
        } else {
          this.success = 'Đã kiểm tra các quy tắc đang bật và cập nhật kết quả.';
        }
      },
      error: error => { if (!this.current()) { return; } this.running = false; this.fail(error, 'Chưa kiểm tra được các quy tắc. Bạn thử lại sau nhé.'); }
    }));
  }
  loadCatalogDemand(): void {
    if (this.demandLoading || !this.current()) { return; }
    this.demandLoading = true;
    this.demandError = '';
    this.requests.add(this.automations.catalogDemand().subscribe({
      next: data => { if (!this.current()) { return; } this.catalogDemand = data; this.demandLoading = false; },
      error: error => {
        if (!this.current()) { return; }
        this.demandLoading = false;
        if (error?.status === 401 || error?.status === 403) { this.leaveSession(); return; }
        this.demandError = 'Chưa tải được nhu cầu tìm sách. Các quy tắc tự động hóa vẫn hoạt động bình thường.';
      }
    }));
  }
}

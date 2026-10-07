import { Component, DoCheck, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { AuthService } from 'src/app/services/auth.service';
import { SupportDraft, SupportRequest, SupportRequestService } from 'src/app/services/support-request.service';
@Component({ selector: 'app-support-request', templateUrl: './support-request.component.html', styleUrls: ['./support-request.component.scss'] })
export class SupportRequestComponent implements OnChanges, DoCheck, OnDestroy {
  @Input() kind: 'RETURN' | 'SUPPORT' = 'RETURN'; @Input() orderId = 0; @Input() summary = '';
  @Input() dismissable = true;
  @Output() closed = new EventEmitter<void>(); @Output() busyChange = new EventEmitter<boolean>(true); @Output() updated = new EventEmitter<void>();
  items: { productId: number; name: string; quantity: number }[] = [];
  productId = 0; receivedOn = ''; outcome = 'EXCHANGE'; message = '';
  review?: SupportDraft; result?: SupportRequest; busy = false; uncertain = false; error = '';
  private version = 0; private owner = ''; private requestKey = '';
  readonly today = (() => { const now = new Date(); return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10); })();
  constructor(private support: SupportRequestService, private auth: AuthService) {}
  private journal(): string { return 'vinabook_pending_support:' + encodeURIComponent(this.owner); }
  private freshKey(): string { return 'support-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2); }
  private setBusy(value: boolean): void { this.busy = value; this.busyChange.emit(value); this.updated.emit(); }
  private current(version: number, owner: string): boolean { return version === this.version && owner === (this.auth.currentUser?.email || ''); }
  ngOnChanges(): void {
    this.version++; this.owner = this.auth.currentUser?.email || ''; this.items = []; this.review = undefined; this.result = undefined;
    this.productId = 0; this.receivedOn = ''; this.message = this.summary.slice(0, 2000); this.error = ''; this.setBusy(false);
    this.requestKey = this.freshKey(); this.uncertain = false;
    try { const key = sessionStorage.getItem(this.journal()); if (key) { this.requestKey = key; this.uncertain = true; } } catch {}
    if (this.uncertain) { void this.recover(); return; }
    if (this.kind === 'RETURN') void this.loadOptions();
  }
  ngDoCheck(): void {
    if (this.owner !== (this.auth.currentUser?.email || '')) {
      this.version++; this.owner = this.auth.currentUser?.email || ''; this.items = []; this.review = undefined; this.result = undefined;
      this.message = ''; this.productId = 0; this.receivedOn = ''; this.outcome = 'EXCHANGE';
      this.uncertain = false; this.error = ''; this.requestKey = this.freshKey(); this.setBusy(false); this.closed.emit();
    }
  }
  async loadOptions(): Promise<void> {
    if (this.busy || !this.owner || !this.orderId) return;
    const version = this.version, owner = this.owner; this.setBusy(true); this.error = '';
    try {
      const options = await this.support.returnOptions(this.orderId).toPromise();
      if (!this.current(version, owner)) return;
      this.items = options.items; this.productId = this.items.length === 1 ? this.items[0].productId : 0;
    } catch (error) { if (this.current(version, owner)) this.error = (error as any)?.error?.message || 'Mình chưa tải được sách trong đơn. Bạn thử lại nhé.'; }
    finally { if (this.current(version, owner)) this.setBusy(false); }
  }
  prepare(): void {
    if (this.busy || this.uncertain || this.result) return;
    if (!this.owner) { this.error = 'Bạn đăng nhập để yêu cầu được lưu đúng tài khoản và nhận được phản hồi nhé.'; return; }
    if (this.message.trim().length < 5 || this.message.trim().length > 2000) { this.error = 'Bạn mô tả ít nhất 5 ký tự về điều cần hỗ trợ nhé, để nhân viên hiểu tình huống và hướng xử lý bạn muốn.'; return; }
    if (this.kind === 'RETURN' && (!this.items.some(item => item.productId === Number(this.productId)) || !this.receivedOn || this.receivedOn > this.today)) {
      this.error = 'Bạn chọn cuốn trong đơn và ngày nhận sách thực tế nhé. Cửa hàng cần hai thông tin này để đối chiếu đúng sách và điều kiện đổi trả.'; return;
    }
    this.error = ''; this.review = { requestKey: this.requestKey, kind: this.kind, message: this.message.trim(), confirmed: true };
    if (this.orderId) this.review.orderId = this.orderId;
    if (this.kind === 'RETURN') Object.assign(this.review, { productId: Number(this.productId), receivedOn: this.receivedOn, outcome: this.outcome });
    this.updated.emit();
  }
  async confirm(): Promise<void> {
    if (this.busy || this.uncertain || this.result || !this.review) return;
    const version = this.version, owner = this.owner, draft = { ...this.review };
    if (!this.current(version, owner)) return;
    this.error = ''; this.setBusy(true);
    try {
      try { sessionStorage.setItem(this.journal(), draft.requestKey); } catch {}
      const result = await this.support.submit(draft).toPromise();
      if (!this.current(version, owner)) return; this.complete(result);
    } catch (error) { if (this.current(version, owner)) { this.uncertain = true; this.error = (error as any)?.error?.message || 'Mình chưa xác nhận được yêu cầu đã lưu chưa. Bạn kiểm tra kết quả trước khi gửi lại, để tránh tạo yêu cầu trùng nhé.'; } }
    finally { if (this.current(version, owner)) this.setBusy(false); }
  }
  async recover(): Promise<void> {
    if (this.busy || !this.owner) return;
    const version = this.version, owner = this.owner; this.error = ''; this.setBusy(true); let loadReturn = false;
    try { const result = await this.support.recover(this.requestKey).toPromise(); if (this.current(version, owner)) this.complete(result); }
    catch (error) {
      if (this.current(version, owner)) {
        if ((error as any)?.status === 404) {
          this.uncertain = false; try { sessionStorage.removeItem(this.journal()); } catch {}
          this.error = 'Mình chưa tìm thấy yêu cầu đã lưu. Bạn có thể xem lại nội dung rồi xác nhận gửi nhé.';
          loadReturn = this.kind === 'RETURN' && !this.items.length;
        } else { this.uncertain = true; this.error = (error as any)?.error?.message || 'Mình chưa kiểm tra được kết quả. Bạn thử kiểm tra lại khi mạng ổn định nhé.'; }
      }
    } finally { if (this.current(version, owner)) this.setBusy(false); }
    if (loadReturn && this.current(version, owner)) await this.loadOptions();
  }
  private complete(result: SupportRequest): void { this.result = result; this.uncertain = false; this.review = undefined; this.message = ''; try { sessionStorage.removeItem(this.journal()); } catch {} }
  edit(): void { if (!this.busy && !this.uncertain) this.review = undefined; }
  dismiss(): void { if (!this.busy && !this.uncertain) this.closed.emit(); }
  bookName(id?: number): string { return this.items.find(item => item.productId === id)?.name || ''; }
  ngOnDestroy(): void { this.version++; this.message = ''; this.review = undefined; }
}

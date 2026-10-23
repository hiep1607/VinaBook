import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AutomationAdminService, AutomationDashboard, AutomationRule } from '../../services/automation-admin.service';
import { PageService } from '../../services/page.service';
import { SessionService } from '../../services/session.service';
import { AutomationsComponent } from './automations.component';

describe('Automation admin workspace', () => {
  let fixture: ComponentFixture<AutomationsComponent>;
  let component: AutomationsComponent;
  let service: any;
  let session: any;
  let token: string | null;
  let admin: boolean;
  let source: AutomationDashboard;
  const cart: AutomationRule = { key: 'CART_REMINDER', label: 'Nhắc giỏ hàng', description: 'Giỏ không thay đổi đủ thời gian → nhắc khách quay lại.', enabled: true, delayHours: 24, threshold: null };
  const stock: AutomationRule = { key: 'LOW_STOCK', label: 'Cảnh báo tồn kho', description: 'Tồn kho chạm ngưỡng → báo cho quản trị viên.', enabled: true, delayHours: null, threshold: 5 };

  beforeEach(async () => {
    token = 'adminA'; admin = true;
    session = { getToken: () => token, isAdmin: () => admin, getUser: () => token ? 'admin@example.test' : null };
    source = { rules: [{ ...cart }, { ...stock }], stats: { unreadLowStock: 1, waitingReaders: 3, notificationsToday: 4, failedDeliveries: 0 }, lowStock: [{ productId: 7, name: 'Sách cần nhập', quantity: 0, threshold: 5, waitingReaders: 3 }], demand: [{ productId: 7, name: 'Sách cần nhập', quantity: 0, waitingReaders: 3 }], recentRuns: [{ id: 'run1', startedAt: '2026-10-21T05:00:00Z', status: 'SUCCESS', createdCount: 4, error: null }] };
    service = { get: jasmine.createSpy('get').and.callFake(() => of(source)), save: jasmine.createSpy('save').and.callFake((rule: AutomationRule) => of({ ...rule })), run: jasmine.createSpy('run').and.callFake(() => of(source)), catalogDemand: jasmine.createSpy('catalogDemand').and.returnValue(of({ days: 30, items: [{ title: 'Cuốn sách chưa nhập', requestCount: 2, lastRequestedAt: '2026-10-21T05:00:00Z' }] })) };
    await TestBed.configureTestingModule({ imports: [CommonModule, FormsModule, RouterTestingModule], declarations: [AutomationsComponent], providers: [{ provide: AutomationAdminService, useValue: service }, { provide: SessionService, useValue: session }, { provide: PageService, useValue: { setPageActive: () => {} } }] }).compileComponents();
    fixture = TestBed.createComponent(AutomationsComponent);
    component = fixture.componentInstance;
  });
  afterEach(() => fixture.destroy());
  const render = () => fixture.detectChanges();

  it('shows server rules, actual stock demand and run results', () => {
    render();
    const text = fixture.nativeElement.textContent;
    expect(fixture.nativeElement.querySelectorAll('.rule-row').length).toBe(2);
    expect(text).toContain('Sách cần nhập');
    expect(text).toContain('Hoàn tất');
    expect(text).toContain('Thông báo tạo hôm nay');
    expect(text).toContain('Lượt kiểm tra thủ công cập nhật thông báo trong web');
    expect(text).toContain('Cuốn sách chưa nhập');
    expect(text).toContain('Lượt tìm');
  });

  it('edits a rule copy and sends only one save while pending', () => {
    render();
    const pending = new Subject<AutomationRule>();
    service.save.and.returnValue(pending);
    component.rules[0].delayHours = 48;
    expect(source.rules[0].delayHours).toBe(24);
    component.save(component.rules[0]); component.save(component.rules[0]);
    expect(service.save).toHaveBeenCalledTimes(1);
    expect(service.save.calls.mostRecent().args[0].delayHours).toBe(48);
    pending.next({ ...cart, delayHours: 48 });
    expect(component.savingKey).toBe('');
    expect(component.dirty(component.rules[0])).toBeFalse();
    expect(component.success).toContain('Đã lưu');
  });

  it('rejects invalid delay including null, fractional or out-of-range input', () => {
    render();
    for (const value of [null, 0, 721, 1.5]) { component.rules[0].delayHours = value; component.save(component.rules[0]); }
    expect(service.save).not.toHaveBeenCalled();
    expect(component.ruleErrors.CART_REMINDER).toContain('1 đến 720');
  });

  it('rejects invalid stock thresholds and allows zero to track only sold-out books', () => {
    render();
    for (const value of [null, -1, 10001, 1.5]) { component.rules[1].threshold = value; component.save(component.rules[1]); }
    expect(service.save).not.toHaveBeenCalled();
    component.rules[1].threshold = 0; component.save(component.rules[1]);
    expect(service.save).toHaveBeenCalledTimes(1);
    expect(component.rules[1].threshold).toBe(0);
  });

  it('keeps unsaved edits on failed save and restores the last saved values explicitly', () => {
    service.save.and.returnValue(throwError({ status: 500 }));
    render();
    component.rules[0].enabled = false;
    component.save(component.rules[0]);
    expect(component.rules[0].enabled).toBeFalse();
    expect(component.ruleErrors.CART_REMINDER).toContain('giữ lại');
    component.restore(component.rules[0]);
    expect(component.rules[0].enabled).toBeTrue();
    expect(component.ruleErrors.CART_REMINDER).toBeUndefined();
    expect(component.hasUnsavedChanges).toBeFalse();
  });

  it('protects unsaved rule edits from reload or a manual scan', () => {
    render();
    component.rules[0].delayHours = 48;
    component.load(); component.run();
    expect(service.get).toHaveBeenCalledTimes(1);
    expect(service.run).not.toHaveBeenCalled();
    render();
    expect(fixture.nativeElement.querySelector('.unsaved-note')).toBeTruthy();
  });

  it('prevents overlapping scans and replaces results after a successful scan', () => {
    render();
    const pending = new Subject<AutomationDashboard>();
    service.run.and.returnValue(pending);
    component.run(); component.run(); component.load();
    expect(service.run).toHaveBeenCalledTimes(1);
    expect(component.running).toBeTrue();
    pending.next({ ...source, stats: { ...source.stats, notificationsToday: 5 } });
    expect(component.running).toBeFalse();
    expect(component.data?.stats.notificationsToday).toBe(5);
    expect(component.success).toContain('Đã kiểm tra');
  });

  it('preserves the previous dashboard on refresh or scan failure', () => {
    render();
    service.get.and.returnValue(throwError({ status: 0 })); component.load();
    expect(component.data?.lowStock[0].productId).toBe(7);
    expect(component.error).toContain('lần tải gần nhất');
    service.run.and.returnValue(throwError({ status: 503 })); component.run();
    expect(component.running).toBeFalse();
    expect(component.data?.stats.waitingReaders).toBe(3);
    expect(component.error).toContain('Chưa kiểm tra');
  });

  it('reports a failed recorded scan as an error even when the endpoint returns a summary successfully', () => {
    render();
    service.run.and.returnValue(of({ ...source, recentRuns: [{ id: 'failed-run', startedAt: '2026-10-21T06:00:00Z', status: 'FAILED', createdCount: 0, error: 'Lần kiểm tra chưa hoàn tất. Thay đổi đã được hoàn tác.' }] }));
    component.run(); render();
    expect(component.running).toBeFalse();
    expect(component.success).toBe('');
    expect(component.error).toContain('được hoàn tác');
    expect(fixture.nativeElement.querySelector('.error-feedback')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.success-feedback')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('.history-panel').textContent).toContain('Có lỗi');
    expect(component.data?.recentRuns[0].id).toBe('failed-run');
  });

  it('shows an initial error with a working retry and honest empty sections', () => {
    service.get.and.returnValue(throwError({ status: 500 }));
    render();
    expect(component.data).toBeUndefined();
    expect(fixture.nativeElement.querySelector('[role=alert]')).toBeTruthy();
    service.get.and.returnValue(of({ ...source, lowStock: [], demand: [], recentRuns: [] }));
    fixture.nativeElement.querySelector('.feedback-retry').click(); render();
    expect(component.error).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Chưa có yêu cầu chờ sách');
    expect(fixture.nativeElement.textContent).toContain('Chưa có lượt kiểm tra');
  });

  it('rejects a non-admin session before requesting data', () => {
    admin = false;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    render();
    expect(service.get).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(component.data).toBeUndefined();
  });

  it('clears private dashboard data and ignores late results when the account changes', () => {
    render();
    const pending = new Subject<AutomationDashboard>();
    service.get.and.returnValue(pending); component.load();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    token = 'adminB';
    pending.next(source);
    component.ngDoCheck();
    expect(component.data).toBeUndefined();
    expect(component.rules).toEqual([]);
    expect(component.success).toBe('');
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(pending.observers.length).toBe(0);
  });

  it('clears private data immediately on forbidden API response', () => {
    render();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    service.run.and.returnValue(throwError({ status: 403 }));
    component.run();
    expect(component.data).toBeUndefined();
    expect(component.rules).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not render a stale save message in a different session', () => {
    render();
    const pending = new Subject<AutomationRule>();
    service.save.and.returnValue(pending);
    component.rules[0].enabled = false; component.save(component.rules[0]);
    token = null;
    pending.next({ ...cart, enabled: false });
    expect(component.success).toBe('');
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    component.ngDoCheck();
    expect(component.rules.length).toBe(0);
  });

  it('keeps the main dashboard available when the missing-title demand endpoint fails', () => {
    service.catalogDemand.and.returnValue(throwError({ status: 503 }));
    render();
    expect(component.data?.rules.length).toBe(2);
    expect(component.error).toBe('');
    expect(component.demandError).toContain('Chưa tải được');
    service.catalogDemand.and.returnValue(of({ days: 30, items: [] }));
    component.loadCatalogDemand(); render();
    expect(component.demandError).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Chưa ghi nhận tên sách');
  });
});

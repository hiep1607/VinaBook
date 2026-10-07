import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { SupportRequestService } from 'src/app/services/support-request.service';
import { SupportRequestComponent } from './support-request.component';
describe('Request review, ownership and recovery', () => {
  let fixture: ComponentFixture<SupportRequestComponent>, component: SupportRequestComponent, support: any, auth: any;
  const journal = 'vinabook_pending_support:' + encodeURIComponent('buyer@test.invalid');
  const result = { id: 9, status: 'OPEN', kind: 'RETURN', reply: '', message: 'Sách thiếu trang.' };
  beforeEach(async () => {
    sessionStorage.removeItem(journal); auth = { currentUser: { email: 'buyer@test.invalid' } };
    support = { returnOptions: jasmine.createSpy().and.returnValue(of({ orderId: 7, items: [{ productId: 2, name: 'Sách thử', quantity: 1 }] })),
      submit: jasmine.createSpy().and.returnValue(of(result)), recover: jasmine.createSpy().and.returnValue(of(result)) };
    await TestBed.configureTestingModule({ imports: [CommonModule, FormsModule], declarations: [SupportRequestComponent],
      providers: [{ provide: AuthService, useValue: auth }, { provide: SupportRequestService, useValue: support }] }).compileComponents();
    fixture = TestBed.createComponent(SupportRequestComponent); component = fixture.componentInstance; component.orderId = 7;
    fixture.ngZone!.run(() => component.ngOnChanges()); await fixture.whenStable(); fixture.detectChanges();
    component.receivedOn = component.today; component.message = 'Sách thiếu trang, xin đổi sách.';
  });
  afterEach(() => { fixture.destroy(); sessionStorage.removeItem(journal); });
  it('requires review and a separate confirmation before saving', async () => {
    component.prepare(); fixture.detectChanges(); expect(support.submit).not.toHaveBeenCalled(); expect(fixture.nativeElement.textContent).toContain('Kiểm tra trước khi gửi');
    await component.confirm(); expect(support.submit).toHaveBeenCalledTimes(1); expect(component.result?.id).toBe(9);
  });
  it('does not assume a receipt date or permit a book outside the selected order', () => {
    component.receivedOn = ''; component.prepare(); expect(component.review).toBeUndefined();
    component.receivedOn = component.today; component.productId = 999; component.prepare(); expect(component.review).toBeUndefined();
  });
  it('blocks repeated clicks and stores only a recovery key while a request is pending', async () => {
    const response = new Subject<any>(); support.submit.and.returnValue(response); component.prepare(); const first = component.confirm(); void component.confirm();
    expect(support.submit).toHaveBeenCalledTimes(1); expect(sessionStorage.getItem(journal)).not.toContain(component.message);
    response.next(result); response.complete(); await first; expect(sessionStorage.getItem(journal)).toBeNull();
  });
  it('recovers an uncertain result without submitting another ticket', async () => {
    support.submit.and.returnValue(throwError({ status: 0 })); component.prepare(); await component.confirm(); await component.confirm();
    expect(component.uncertain).toBeTrue(); await component.recover(); expect(component.result?.id).toBe(9); expect(support.submit).toHaveBeenCalledTimes(1);
  });
  it('ignores responses and clears private form data after changing accounts', async () => {
    const response = new Subject<any>(); support.submit.and.returnValue(response); component.prepare(); const pending = component.confirm();
    auth.currentUser = { email: 'other@test.invalid' }; component.ngDoCheck(); response.next(result); response.complete(); await pending;
    expect(component.result).toBeUndefined(); expect(component.message).toBe('');
  });
  it('lets a customer review a support message without requiring return-specific data', async () => {
    component.kind = 'SUPPORT'; component.orderId = 0; component.summary = 'Nhờ nhân viên kiểm tra giao hàng.'; component.ngOnChanges();
    component.prepare(); expect(component.review?.kind).toBe('SUPPORT'); expect(component.review?.receivedOn).toBeUndefined();
    expect(support.submit).not.toHaveBeenCalled(); await component.confirm(); expect(support.submit).toHaveBeenCalledTimes(1);
  });
});

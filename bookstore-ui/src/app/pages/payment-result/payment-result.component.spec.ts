import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { PaymentService } from 'src/app/services/payment.service';
import { NotificationService } from 'src/app/services/notification.service';
import { PaymentResultComponent } from './payment-result.component';

describe('Payment result recovery and account isolation', () => {
  let fixture: ComponentFixture<PaymentResultComponent>, component: PaymentResultComponent;
  let params: BehaviorSubject<any>, payments: any, auth: any, notification: any;
  const pending = {orderId:7,amount:120000,paymentMethod:'VNPAY',paymentStatus:'PENDING',orderStatus:0,methodAvailable:true,canStart:true,canPoll:true};
  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({orderId:'7',success:'true'}));
    payments = {getStatus:jasmine.createSpy().and.returnValue(of({...pending})),start:jasmine.createSpy().and.returnValue(new Subject())};
    auth = {currentUser:{email:'reader@example.com'},isLoggedIn:()=>true};
    notification = {error:jasmine.createSpy(),success:jasmine.createSpy(),info:jasmine.createSpy()};
    await TestBed.configureTestingModule({imports:[CommonModule,RouterTestingModule],declarations:[PaymentResultComponent],providers:[
      {provide:ActivatedRoute,useValue:{queryParamMap:params}},{provide:PaymentService,useValue:payments},
      {provide:AuthService,useValue:auth},{provide:NotificationService,useValue:notification}
    ]}).compileComponents();
    spyOn(TestBed.inject(Router),'navigate').and.returnValue(Promise.resolve(true));
  });
  const run = (test:()=>void) => fakeAsync(() => {
    fixture=TestBed.createComponent(PaymentResultComponent); component=fixture.componentInstance;
    fixture.detectChanges(); tick(0); fixture.detectChanges();
    try { test(); } finally { fixture.destroy(); }
  });
  it('ignores success on URL and offers a continuation for an unpaid existing order',run(()=>{
    expect(component.payment?.paymentStatus).toBe('PENDING');
    expect(fixture.nativeElement.textContent).toContain('Tiếp tục thanh toán');
    expect(fixture.nativeElement.textContent).not.toContain('Thanh toán thành công');
    component.retryPayment();component.retryPayment();expect(payments.start).toHaveBeenCalledOnceWith(7);
  }));
  it('stops polling after confirmed payment and links to this order',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,paymentStatus:'PAID',canStart:false,canPoll:false}));
    component.loadStatus();fixture.detectChanges();const count=payments.getStatus.calls.count();tick(24000);
    expect(payments.getStatus.calls.count()).toBe(count);expect(component.canPay).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Thanh toán thành công');component.goToOrder();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/orders',7]);
  }));
  it('does not show a transfer QR or allow payment for a cancelled order',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,paymentMethod:'BANK_QR',orderStatus:3,qrUrl:'https://example.invalid/qr.png'}));
    component.loadStatus();fixture.detectChanges();component.retryPayment();
    expect(fixture.nativeElement.textContent).toContain('Đơn hàng đã hủy');
    expect(fixture.nativeElement.querySelector('.qr-frame')).toBeNull();expect(payments.start).not.toHaveBeenCalled();
  }));
  it('shows COD instructions and does not poll indefinitely',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,paymentMethod:'COD',paymentStatus:'UNPAID'}));component.loadStatus();fixture.detectChanges();
    const count=payments.getStatus.calls.count();tick(24000);
    expect(payments.getStatus.calls.count()).toBe(count);expect(component.canPay).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('nhân viên giao hàng');
  }));
  it('explains an unavailable provider without opening a new payment',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,methodAvailable:false,canStart:false}));component.loadStatus();fixture.detectChanges();component.retryPayment();
    expect(fixture.nativeElement.textContent).toContain('tạm thời chưa khả dụng');expect(payments.start).not.toHaveBeenCalled();
  }));
  it('keeps an uncertain pending transaction for reconciliation instead of starting another',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,canStart:false}));component.loadStatus();fixture.detectChanges();component.retryPayment();
    expect(fixture.nativeElement.textContent).toContain('liên hệ cửa hàng kèm mã đơn');
    expect(fixture.nativeElement.textContent).not.toContain('Tiếp tục thanh toán');expect(payments.start).not.toHaveBeenCalled();
  }));
  it('keeps the last known payment during network failure and supports manual recovery',run(()=>{
    payments.getStatus.and.returnValue(throwError({status:503}));component.loadStatus();
    expect(component.payment?.paymentStatus).toBe('PENDING');expect(component.loadError).toContain('đợi xác nhận');
    const count=payments.getStatus.calls.count();tick(24000);expect(payments.getStatus.calls.count()).toBe(count);
    payments.getStatus.and.returnValue(of({...pending,paymentStatus:'PAID'}));component.loadStatus();expect(component.payment?.paymentStatus).toBe('PAID');
  }));
  it('bounds automatic checks without interpreting the wait as a failure',run(()=>{
    tick(310000);const count=payments.getStatus.calls.count();tick(24000);
    expect(component.pollingPaused).toBeTrue();expect(count).toBeLessThanOrEqual(38);
    expect(payments.getStatus.calls.count()).toBe(count);expect(component.payment?.paymentStatus).toBe('PENDING');
  }));
  it('never overlaps status requests',run(()=>{
    const slow=new Subject();payments.getStatus.and.returnValue(slow);component.loadStatus();component.loadStatus();tick(8000);
    expect(payments.getStatus.calls.count()).toBe(2);slow.next({...pending});
  }));
  it('discards a delayed status response after changing the order URL',run(()=>{
    const slow=new Subject();payments.getStatus.and.returnValue(slow);component.loadStatus();
    payments.getStatus.and.returnValue(of({...pending,orderId:8}));params.next(convertToParamMap({orderId:'8'}));tick(0);
    slow.next({...pending,paymentStatus:'PAID'});expect(component.payment?.orderId).toBe(8);expect(component.payment?.paymentStatus).toBe('PENDING');
  }));
  it('clears private payment data and cancels requests after switching accounts',run(()=>{
    const slow=new Subject();payments.getStatus.and.returnValue(slow);component.loadStatus();
    auth.currentUser.email='another@example.com';component.ngDoCheck();slow.next({...pending});
    expect(component.payment).toBeNull();expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'],jasmine.anything());
  }));
  it('hides stale details if access is denied',run(()=>{
    payments.getStatus.and.returnValue(throwError({status:403}));component.loadStatus();expect(component.payment).toBeNull();expect(component.loadError).toContain('tài khoản của bạn');
  }));
  it('rejects invalid order IDs without calling the API',run(()=>{
    const count=payments.getStatus.calls.count();params.next(convertToParamMap({orderId:'-2'}));component.loadStatus();
    expect(payments.getStatus.calls.count()).toBe(count);expect(component.loadError).toContain('hợp lệ');
  }));
  it('keeps transfer details when the QR image cannot load',run(()=>{
    payments.getStatus.and.returnValue(of({...pending,paymentMethod:'BANK_QR',bankAccountNo:'123456',qrUrl:'https://example.invalid/qr.png'}));
    component.loadStatus();fixture.detectChanges();fixture.nativeElement.querySelector('.qr-frame img').dispatchEvent(new Event('error'));fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa tải được mã QR');expect(fixture.nativeElement.textContent).toContain('123456');
  }));
});

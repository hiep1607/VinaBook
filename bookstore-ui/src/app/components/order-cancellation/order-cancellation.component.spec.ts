import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { OrderService } from 'src/app/services/order.service';
import { OrderCancellationComponent } from './order-cancellation.component';

describe('Shared order cancellation', () => {
  let fixture: ComponentFixture<OrderCancellationComponent>, component: OrderCancellationComponent;
  let orders: any, auth: any;
  const preview = { orderId: 7, orderDate: '2026-10-07', total: 120000, statusCode: 0,
    canCancel: true, reason: '', items: [{ name: 'Sách thử hủy', quantity: 1 }] };
  beforeEach(async () => {
    auth = { currentUser: { email: 'buyer@test.invalid' } };
    orders = { cancellationPreview: jasmine.createSpy().and.returnValue(of({ ...preview })),
      cancelOrder: jasmine.createSpy().and.returnValue(of({ orderId: 7, status: 'CANCELLED' })) };
    await TestBed.configureTestingModule({ imports: [CommonModule], declarations: [OrderCancellationComponent],
      providers: [{ provide: AuthService, useValue: auth }, { provide: OrderService, useValue: orders }] }).compileComponents();
    fixture = TestBed.createComponent(OrderCancellationComponent); component = fixture.componentInstance;
    component.orderId = 7; component.ngOnChanges(); fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());
  it('shows the target books and total before any mutation and allows keeping the order', () => {
    expect(fixture.nativeElement.textContent).toContain('Sách thử hủy');
    expect(fixture.nativeElement.textContent).toContain('Đơn #7');
    expect(orders.cancelOrder).not.toHaveBeenCalled();
    const closed = spyOn(component.closed, 'emit'); component.dismiss(); expect(closed).toHaveBeenCalled();
  });
  it('confirms only once while a request is pending or done', () => {
    const response = new Subject<any>(); orders.cancelOrder.and.returnValue(response);
    component.confirm(); component.confirm(); expect(orders.cancelOrder).toHaveBeenCalledTimes(1);
    response.next({ orderId: 7, status: 'CANCELLED' }); component.confirm();
    expect(orders.cancelOrder).toHaveBeenCalledTimes(1); expect(component.done).toBeTrue();
  });
  it('checks status after an uncertain result before permitting another confirmation', () => {
    orders.cancelOrder.and.returnValue(throwError({ status: 0 })); component.confirm();
    expect(component.uncertain).toBeTrue(); component.confirm(); expect(orders.cancelOrder).toHaveBeenCalledTimes(1);
    orders.cancellationPreview.and.returnValue(of({ ...preview, canCancel: false, statusCode: 3 }));
    component.load(); expect(component.done).toBeTrue(); expect(orders.cancelOrder).toHaveBeenCalledTimes(1);
  });
  it('blocks ineligible orders and shows the reason', () => {
    orders.cancellationPreview.and.returnValue(of({ ...preview, canCancel: false, reason: 'Đơn đang giao.' }));
    component.ngOnChanges(); component.confirm(); fixture.detectChanges();
    expect(orders.cancelOrder).not.toHaveBeenCalled(); expect(fixture.nativeElement.textContent).toContain('Đơn đang giao.');
  });
  it('clears private state and ignores an old response when the account changes', () => {
    const response = new Subject<any>(); orders.cancelOrder.and.returnValue(response); component.confirm();
    auth.currentUser = { email: 'other@test.invalid' }; component.ngDoCheck(); response.next({ orderId: 7, status: 'CANCELLED' });
    expect(component.preview).toBeUndefined(); expect(component.done).toBeFalse(); expect(component.busy).toBeFalse();
  });
  it('does not load or cancel for a guest', () => {
    auth.currentUser = null; orders.cancellationPreview.calls.reset(); component.ngOnChanges(); component.confirm();
    expect(orders.cancellationPreview).not.toHaveBeenCalled(); expect(orders.cancelOrder).not.toHaveBeenCalled();
  });
  it('replaces an in-flight preview when a different order is selected', () => {
    const response = new Subject<any>(); orders.cancellationPreview.and.returnValue(response); component.ngOnChanges();
    orders.cancellationPreview.and.returnValue(of({ ...preview, orderId: 8 })); component.orderId = 8; component.ngOnChanges();
    response.next(preview); expect(component.preview?.orderId).toBe(8); expect(component.busy).toBeFalse();
  });
});

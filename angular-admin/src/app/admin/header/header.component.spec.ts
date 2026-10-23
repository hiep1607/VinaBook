import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, Subject } from 'rxjs';
import { ToastrService } from 'ngx-toastr';
import { CustomerService } from '../../services/customer.service';
import { NotificationService } from '../../services/notification.service';
import { SessionService } from '../../services/session.service';
import { WebSocketService } from '../../services/web-socket.service';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let toast: jasmine.SpyObj<ToastrService>;
  let readAllReply: Subject<any>;

  beforeEach(async () => {
    notifications = jasmine.createSpyObj('NotificationService', ['get', 'readed', 'readAll']);
    toast = jasmine.createSpyObj('ToastrService', ['error', 'info']);
    readAllReply = new Subject<any>();
    notifications.get.and.returnValue(of([
      { id: 1, message: 'Đơn mới', status: false, time: new Date() },
      { id: 2, message: 'Đơn đã hủy', status: false, time: new Date() }
    ]));
    notifications.readAll.and.returnValue(readAllReply);
    await TestBed.configureTestingModule({
      declarations: [HeaderComponent],
      providers: [
        { provide: NotificationService, useValue: notifications },
        { provide: ToastrService, useValue: toast },
        { provide: WebSocketService, useValue: { watch: () => NEVER } },
        { provide: SessionService, useValue: { getUser: () => 'admin@example.test', signOut: () => {} } },
        { provide: CustomerService, useValue: { getByEmail: () => of({ name: 'Admin', image: '' }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the header with the current unread count', () => {
    expect(component).toBeTruthy();
    expect(component.getNotificationFalse()).toBe(2);
  });

  it('refreshes the badge only after read-all has been saved by the server', () => {
    notifications.get.calls.reset();
    notifications.get.and.returnValue(of([
      { id: 1, message: 'Đơn mới', status: true, time: new Date() },
      { id: 2, message: 'Đơn đã hủy', status: true, time: new Date() }
    ]));
    component.readAll();
    expect(notifications.readAll).toHaveBeenCalledTimes(1);
    expect(notifications.readed).not.toHaveBeenCalled();
    expect(notifications.get).not.toHaveBeenCalled();
    expect(component.getNotificationFalse()).toBe(2);
    readAllReply.next(null);
    readAllReply.complete();
    expect(notifications.get).toHaveBeenCalledTimes(1);
    expect(component.getNotificationFalse()).toBe(0);
  });

  it('keeps unread notices and reports a read-all failure', () => {
    notifications.get.calls.reset();
    component.readAll();
    readAllReply.error(new Error('Server unavailable'));
    expect(notifications.get).not.toHaveBeenCalled();
    expect(component.getNotificationFalse()).toBe(2);
    expect(toast.error).toHaveBeenCalled();
  });
});

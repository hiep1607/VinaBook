import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { environment } from 'src/environments/environment';

import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ForgotPasswordComponent],
      imports: [HttpClientTestingModule]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('gửi POST tới endpoint quên mật khẩu với email dạng text/plain', () => {
    component.email = 'user@example.com';
    component.request();

    const req = httpMock.expectOne(environment.apiUrl + '/api/auth/send-mail-forgot-password-token');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBe('user@example.com');

    req.flush('');
    expect(component.sent).toBe(true);
    expect(component.loading).toBe(false);
  });

  it('không gửi khi email rỗng hoặc sai định dạng', () => {
    component.email = '';
    component.request();

    component.email = 'khong-hop-le';
    component.request();

    httpMock.expectNone(environment.apiUrl + '/api/auth/send-mail-forgot-password-token');
    expect(component.sent).toBe(false);
  });

  it('lỗi mạng hiển thị thông báo lỗi trung tính', () => {
    component.email = 'user@example.com';
    component.request();

    const req = httpMock.expectOne(environment.apiUrl + '/api/auth/send-mail-forgot-password-token');
    req.error(new ErrorEvent('network error'));

    expect(component.sent).toBe(false);
    expect(component.submitError).toContain('Không gửi được yêu cầu');
  });
});

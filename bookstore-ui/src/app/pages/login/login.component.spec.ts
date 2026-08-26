import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ LoginComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('toggleShowPassword đổi trạng thái hiện/ẩn mật khẩu', () => {
    expect(component.showPassword).toBe(false);

    component.toggleShowPassword();
    expect(component.showPassword).toBe(true);

    component.toggleShowPassword();
    expect(component.showPassword).toBe(false);
  });
});

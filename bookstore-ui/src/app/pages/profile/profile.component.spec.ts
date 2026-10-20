import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { ProfileComponent } from './profile.component';
import { DeliveryAddressComponent } from '../../components/delivery-address/delivery-address.component';
import { AuthService } from '../../services/auth.service';
import { OrderService } from '../../services/order.service';
import { CartService } from '../../services/cart.service';
import { NotificationService } from '../../services/notification.service';
import { AddressService } from '../../services/address.service';
import { ReviewService } from '../../services/review.service';
import { DeliveryLocationService } from '../../services/delivery-location.service';

describe('Account profile layout and editing', () => {
  let fixture: ComponentFixture<ProfileComponent>, component: ProfileComponent, auth: any, addresses: any;
  const savedAddress = {id:1,label:'Nhà riêng',name:'Minh An',phone:'0901234567',street:'12 Đường A, Hà Nội',isDefault:true};
  beforeEach(async () => {
    auth = {isLoggedIn:()=>true,currentUser:{token:'session',email:'reader@example.test',name:'Minh An',phone:'0901234567',avatar:'',registerDate:'2026-01-10'},
      updateUser:jasmine.createSpy().and.callFake((value: any)=>Promise.resolve().then(()=>Object.assign(auth.currentUser,value))),logout:jasmine.createSpy()};
    addresses = {refresh:()=>Promise.resolve(),getAddresses:()=>[{...savedAddress}],addAddress:jasmine.createSpy().and.returnValue(Promise.resolve())};
    await TestBed.configureTestingModule({declarations:[ProfileComponent,DeliveryAddressComponent],providers:[
      {provide:AuthService,useValue:auth}, {provide:OrderService,useValue:{getOrderPage:()=>of({items:[],counts:{'-1':0},totalSpent:0})}},
      {provide:CartService,useValue:{getCartCount:()=>0}}, {provide:NotificationService,useValue:{success:jasmine.createSpy(),error:jasmine.createSpy()}},
      {provide:AddressService,useValue:addresses}, {provide:ReviewService,useValue:{countByEmail:()=>0}},
      {provide:DeliveryLocationService,useValue:{provinces:()=>Promise.resolve([{code:1,name:'Hà Nội'}])}}
    ]}).compileComponents();
    fixture=TestBed.createComponent(ProfileComponent);component=fixture.componentInstance;fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  it('shows the profile form and avatar editor together while keeping email read-only', () => {
    expect(fixture.nativeElement.querySelector('#profile-heading').textContent).toContain('Hồ sơ của tôi');
    expect(fixture.nativeElement.querySelector('.profile-avatar-panel')).not.toBeNull();
    const email: HTMLInputElement=fixture.nativeElement.querySelector('#profile-email');
    expect(email.readOnly).toBeTrue();expect(email.value).toBe('reader@example.test');
  });
  it('saves name and phone through the existing account service only once', fakeAsync(() => {
    flushMicrotasks(); component.editName='  An mới  ';component.editPhone='0900000000';
    void component.saveInfo();void component.saveInfo();
    expect(component.savingInfo).toBeTrue();expect(auth.updateUser).toHaveBeenCalledOnceWith({name:'An mới',phone:'0900000000'});
    flushMicrotasks();expect(component.savingInfo).toBeFalse();
  }));
  it('keeps an inline error and unlocks the form when saving fails', fakeAsync(() => {
    flushMicrotasks();auth.updateUser.and.callFake(()=>Promise.reject(new Error('Không lưu được')));
    void component.saveInfo();flushMicrotasks();fixture.detectChanges();
    expect(component.savingInfo).toBeFalse();expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Không lưu được');
  }));
  it('allows viewing and cancelling a selected avatar before any account write', () => {
    const reader:any={readAsDataURL:jasmine.createSpy(),result:'data:image/png;base64,YWJj'};
    spyOn(window as any,'FileReader').and.returnValue(reader);
    component.onAvatarSelected({target:{files:[new File(['abc'],'avatar.png',{type:'image/png'})],value:'avatar.png'}});
    reader.onload();expect(component.avatarDirty).toBeTrue();expect(auth.updateUser).not.toHaveBeenCalled();
    component.cancelAvatar();expect(component.previewAvatar).toBe('');expect(component.avatarDirty).toBeFalse();
  });
  it('discards a cancelled file read that finishes later', () => {
    const reader:any={readAsDataURL:jasmine.createSpy(),result:'data:image/png;base64,YWJj'};
    spyOn(window as any,'FileReader').and.returnValue(reader);
    component.onAvatarSelected({target:{files:[new File(['abc'],'avatar.png',{type:'image/png'})],value:'avatar.png'}});
    component.cancelAvatar();reader.onload();expect(component.previewAvatar).toBe('');
  });
  it('keeps the existing avatar visible if removing it fails', fakeAsync(() => {
    flushMicrotasks();auth.currentUser.avatar='data:image/png;base64,YWJj';component.previewAvatar=auth.currentUser.avatar;
    auth.updateUser.and.callFake(()=>Promise.reject(new Error('Không xoá được')));
    void component.removeAvatar();flushMicrotasks();expect(component.previewAvatar).toBe(auth.currentUser.avatar);expect(component.avatarError).toContain('Không xoá');
  }));
  it('keeps the saved addresses, label combobox and location editor reachable', fakeAsync(() => {
    flushMicrotasks();component.setTab('address');fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.address-street').textContent).toContain(savedAddress.street);
    component.openAddAddress();fixture.detectChanges();flushMicrotasks();fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#address-label').tagName).toBe('SELECT');
    expect(fixture.nativeElement.querySelector('app-delivery-address')).not.toBeNull();
  }));
  it('retains password, purchase and account statistics sections', () => {
    component.setTab('password');fixture.detectChanges();expect(fixture.nativeElement.querySelector('#new-password')).not.toBeNull();
    component.setTab('orders');fixture.detectChanges();expect(fixture.nativeElement.querySelector('.orders-panel')).not.toBeNull();
    component.setTab('stats');fixture.detectChanges();expect(fixture.nativeElement.querySelectorAll('.stat-card').length).toBe(4);
  });
});

import { Component, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { DeliveryLocationService } from '../../services/delivery-location.service';
import { DeliveryAddressComponent } from './delivery-address.component';

@Component({ template: '<form><app-delivery-address name="street" [(ngModel)]="street" [disabled]="disabled" required></app-delivery-address></form>' })
class AddressHost { street = ''; disabled = false; }

describe('Delivery address selection and location', () => {
  let fixture: ComponentFixture<AddressHost>, component: DeliveryAddressComponent, location: any;
  const provinces = [{code: 1, name: 'Thành phố Hà Nội'}, {code: 79, name: 'Thành phố Hồ Chí Minh'}];
  const wards = [{code: 4, name: 'Phường Ba Đình', province_code: 1}];
  beforeEach(async () => {
    location = {
      provinces: jasmine.createSpy().and.callFake(() => Promise.resolve(provinces)),
      wards: jasmine.createSpy().and.callFake(() => Promise.resolve(wards)),
      position: jasmine.createSpy().and.callFake(() => Promise.resolve({coords: {latitude: 21, longitude: 105, accuracy: 25}})),
      reverse: jasmine.createSpy().and.callFake(() => Promise.resolve({street: 'Phố Trần Phú, Ba Đình, Hà Nội', accuracy: 25}))
    };
    await TestBed.configureTestingModule({ declarations: [DeliveryAddressComponent, AddressHost], imports: [FormsModule],
      providers: [{provide: DeliveryLocationService, useValue: location}], schemas: [NO_ERRORS_SCHEMA] }).compileComponents();
    fixture = TestBed.createComponent(AddressHost); fixture.detectChanges();
    component = fixture.debugElement.query(By.directive(DeliveryAddressComponent)).componentInstance;
  });
  afterEach(() => fixture.destroy());

  it('never requests location when a form is opened', () => {
    expect(location.position).not.toHaveBeenCalled(); expect(location.reverse).not.toHaveBeenCalled();
  });
  it('composes the selected ward and province into the existing street contract', fakeAsync(() => {
    flushMicrotasks(); void component.selectProvince('1'); flushMicrotasks();
    component.selectWard('4'); component.detailChanged('12 Trần Phú'); flushMicrotasks();
    expect(fixture.componentInstance.street).toBe('12 Trần Phú, Phường Ba Đình, Thành phố Hà Nội');
    expect(component.validate({} as any)).toBeNull();
  }));
  it('clears the old ward and blocks partial addresses when the province changes', fakeAsync(() => {
    flushMicrotasks(); void component.selectProvince('1'); flushMicrotasks(); component.selectWard('4'); component.detailChanged('12 Trần Phú');
    void component.selectProvince('79'); flushMicrotasks();
    expect(component.wardCode).toBe(''); expect(fixture.componentInstance.street).toBe('');
    expect(component.validate({} as any)).toEqual({addressSelection: true});
  }));
  it('ignores an older ward response after a different province is selected', fakeAsync(() => {
    flushMicrotasks(); let finish: (rows: any[]) => void = () => {};
    location.wards.and.callFake((code: number) => code === 1 ? new Promise(resolve => finish = resolve) : Promise.resolve([{code: 10, name: 'Phường mới', province_code: 79}]));
    void component.selectProvince('1'); void component.selectProvince('79'); flushMicrotasks(); finish(wards); flushMicrotasks();
    expect(component.wards[0].code).toBe(10); expect(component.wardsLoading).toBeFalse();
  }));
  it('keeps an existing address intact for editing without parsing or migrating it', fakeAsync(() => {
    fixture.componentInstance.street = '12 Đường cũ, Quận 1, TP.HCM'; fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    expect(component.mode).toBe('full'); expect(component.fullAddress).toBe('12 Đường cũ, Quận 1, TP.HCM');
    expect(fixture.nativeElement.querySelector('textarea').value).toContain('Quận 1');
  }));
  it('does not replace a typed address until the user accepts the location suggestion', fakeAsync(() => {
    flushMicrotasks(); component.switchMode('full'); component.fullChanged('Địa chỉ đang nhập');
    void component.locate(); flushMicrotasks();
    expect(fixture.componentInstance.street).toBe('Địa chỉ đang nhập'); expect(component.suggestion).toBeDefined();
    component.applySuggestion(); flushMicrotasks();
    expect(fixture.componentInstance.street).toBe('Phố Trần Phú, Ba Đình, Hà Nội'); expect(component.mode).toBe('full');
  }));
  it('preserves manual input after permission is denied', fakeAsync(() => {
    flushMicrotasks(); component.switchMode('full'); component.fullChanged('12 Đường hiện tại');
    location.position.and.callFake(() => Promise.reject(new Error('Bạn chưa cho phép truy cập vị trí.')));
    void component.locate(); flushMicrotasks(); fixture.detectChanges();
    expect(fixture.componentInstance.street).toBe('12 Đường hiện tại'); expect(component.locating).toBeFalse();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('chưa cho phép');
  }));
  it('discards a late lookup after the user edits the address', fakeAsync(() => {
    flushMicrotasks(); let finish: (value: any) => void = () => {};
    location.reverse.and.returnValue(new Promise(resolve => finish = resolve));
    void component.locate(); flushMicrotasks(); component.switchMode('full'); component.fullChanged('Địa chỉ mới của tôi');
    finish({street: 'Kết quả cũ', accuracy: 25}); flushMicrotasks();
    expect(component.suggestion).toBeUndefined(); expect(fixture.componentInstance.street).toBe('Địa chỉ mới của tôi');
  }));
  it('cancels without resolving the device location and ignores later callbacks', fakeAsync(() => {
    flushMicrotasks(); let finish: (value: any) => void = () => {};
    location.position.and.returnValue(new Promise(resolve => finish = resolve));
    void component.locate(); component.cancelLocation(); finish({coords: {latitude: 21, longitude: 105, accuracy: 25}}); flushMicrotasks();
    expect(location.reverse).not.toHaveBeenCalled(); expect(component.locating).toBeFalse();
  }));
  it('offers manual entry when the area API fails', fakeAsync(() => {
    flushMicrotasks(); component.provinces = []; location.provinces.and.callFake(() => Promise.reject(new Error('offline')));
    void component.loadProvinces(); flushMicrotasks(); component.switchMode('full'); component.fullChanged('12 Đường A, Hà Nội'); flushMicrotasks();
    expect(component.areaError).toContain('Chưa tải'); expect(fixture.componentInstance.street).toBe('12 Đường A, Hà Nội');
  }));
  it('does not geocode after the control has been disabled or destroyed', fakeAsync(() => {
    flushMicrotasks(); let finish: (value: any) => void = () => {};
    location.position.and.returnValue(new Promise(resolve => finish = resolve)); void component.locate();
    component.setDisabledState(true); finish({coords: {latitude: 21, longitude: 105, accuracy: 25}}); flushMicrotasks();
    expect(location.reverse).not.toHaveBeenCalled();
    component.ngOnDestroy(); expect(component.suggestion).toBeUndefined();
  }));
});

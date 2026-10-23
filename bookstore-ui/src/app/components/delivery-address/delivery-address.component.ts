import { Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { AddressArea, DeliveryLocationService, LocatedAddress } from '../../services/delivery-location.service';

let nextAddressId = 0;
@Component({
  selector: 'app-delivery-address',
  templateUrl: './delivery-address.component.html',
  styleUrls: ['./delivery-address.component.scss'],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DeliveryAddressComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DeliveryAddressComponent), multi: true }
  ]
})
export class DeliveryAddressComponent implements ControlValueAccessor, Validator, OnInit, OnDestroy {
  @Input() inputId = 'delivery-address-' + ++nextAddressId;
  fullAddress = '';
  mode: 'areas' | 'full' = 'areas';
  detail = '';
  provinceCode = '';
  wardCode = '';
  provinces: AddressArea[] = [];
  wards: AddressArea[] = [];
  provincesLoading = false;
  wardsLoading = false;
  areaError = '';
  locationError = '';
  locating = false;
  locationStatus = '';
  suggestion?: LocatedAddress;
  disabled = false;
  private destroyed = false;
  private locationVersion = 0;
  private areaVersion = 0;
  private value = '';
  private change: (value: string) => void = () => {};
  touched: () => void = () => {};
  private validationChanged: () => void = () => {};

  constructor(private location: DeliveryLocationService) {}
  ngOnInit(): void { void this.loadProvinces(); }
  ngOnDestroy(): void { this.destroyed = true; this.cancelLocation(); this.areaVersion++; }
  writeValue(value: string): void {
    const next = value || '';
    if (next === this.value) return;
    this.cancelLocation();
    this.value = next; this.fullAddress = next;
    this.mode = next ? 'full' : 'areas';
    this.provinceCode = ''; this.wardCode = ''; this.detail = ''; this.wards = []; this.areaVersion++;
  }
  registerOnChange(fn: (value: string) => void): void { this.change = fn; }
  registerOnTouched(fn: () => void): void { this.touched = fn; }
  registerOnValidatorChange(fn: () => void): void { this.validationChanged = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; if (disabled) this.cancelLocation(); }
  validate(_: AbstractControl): ValidationErrors | null {
    return this.mode === 'areas' && (!this.provinceCode || !this.wardCode || !this.detail.trim()) ? { addressSelection: true } : null;
  }

  async loadProvinces(): Promise<void> {
    if (this.provincesLoading || this.provinces.length) return;
    this.provincesLoading = true; this.areaError = '';
    try { const rows = await this.location.provinces(); if (!this.destroyed) this.provinces = rows; }
    catch { if (!this.destroyed) this.areaError = 'Chưa tải được khu vực. Bạn thử lại hoặc chọn nhập địa chỉ đầy đủ.'; }
    finally { if (!this.destroyed) this.provincesLoading = false; }
  }

  async selectProvince(code: string): Promise<void> {
    if (this.disabled) return;
    this.cancelLocation();
    const version = ++this.areaVersion;
    this.provinceCode = code; this.wardCode = ''; this.wards = []; this.areaError = ''; this.wardsLoading = false;
    this.compose();
    if (!code) return;
    this.wardsLoading = true;
    try { const rows = await this.location.wards(Number(code)); if (!this.destroyed && version === this.areaVersion) this.wards = rows; }
    catch { if (!this.destroyed && version === this.areaVersion) this.areaError = 'Chưa tải được phường/xã. Bạn chọn lại tỉnh/thành hoặc nhập địa chỉ đầy đủ.'; }
    finally { if (!this.destroyed && version === this.areaVersion) this.wardsLoading = false; }
  }
  selectWard(code: string): void { if (!this.disabled) { this.wardCode = code; this.compose(); } }
  detailChanged(value: string): void { if (!this.disabled) { this.detail = value; this.compose(); } }
  fullChanged(value: string): void { if (!this.disabled) { this.fullAddress = value; this.cancelLocation(); this.emit(value); } }
  switchMode(mode: 'areas' | 'full'): void {
    if (this.disabled || mode === this.mode) return;
    this.cancelLocation(); this.mode = mode; this.locationError = '';
    if (mode === 'full') { this.fullAddress = this.value || this.fullAddress; this.emit(this.fullAddress); }
    else this.compose();
    this.validationChanged();
  }
  private compose(): void {
    this.cancelLocation();
    const province = this.provinces.find(area => String(area.code) === this.provinceCode);
    const ward = this.wards.find(area => String(area.code) === this.wardCode);
    const street = province && ward && this.detail.trim() ? [this.detail.trim(), ward.name, province.name].join(', ') : '';
    this.emit(street); this.validationChanged();
  }
  private emit(value: string): void { this.value = value; this.change(value); this.touched(); }
  get preview(): string { return this.mode === 'areas' ? this.value : ''; }

  async locate(): Promise<void> {
    if (this.disabled || this.locating) return;
    const version = ++this.locationVersion;
    this.suggestion = undefined; this.locationError = ''; this.locating = true;
    this.locationStatus = 'Đang lấy vị trí từ thiết bị…';
    try {
      const position = await this.location.position();
      if (!this.current(version)) return;
      this.locationStatus = 'Đang tìm địa chỉ gần vị trí của bạn…';
      const address = await this.location.reverse(position);
      if (this.current(version)) this.suggestion = address;
    } catch (error) {
      if (this.current(version)) this.locationError = (error as Error).message || 'Chưa lấy được địa chỉ. Bạn nhập thủ công nhé.';
    } finally { if (this.current(version)) { this.locating = false; this.locationStatus = ''; } }
  }
  applySuggestion(): void {
    if (this.disabled || !this.suggestion) return;
    const street = this.suggestion.street;
    this.cancelLocation(); this.mode = 'full'; this.fullAddress = street;
    this.locationError = ''; this.emit(street); this.validationChanged();
  }
  cancelLocation(): void { this.locationVersion++; this.locating = false; this.locationStatus = ''; this.suggestion = undefined; }
  private current(version: number): boolean { return !this.destroyed && !this.disabled && version === this.locationVersion; }
}

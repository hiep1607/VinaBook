import { shippingError } from 'src/app/shared/shipping-validation';
import { ADDRESS_LABELS } from 'src/app/shared/address-labels';
import { Component, OnInit, DoCheck, OnDestroy } from '@angular/core';

import { Router } from '@angular/router';

import { AuthService } from 'src/app/services/auth.service';
import { OrderService } from 'src/app/services/order.service';
import { CartService } from 'src/app/services/cart.service';
import { NotificationService } from 'src/app/services/notification.service';
import { AddressService } from 'src/app/services/address.service';
import { ReviewService } from 'src/app/services/review.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit, DoCheck, OnDestroy {
  readonly addressLabels = ADDRESS_LABELS;
  private owner='';
  ngDoCheck():void {if(this.owner && this.owner!==(this.authService.currentUser?.token||this.authService.currentUser?.email||'')){this.owner='';this.previewAvatar='';this.addresses=[];this.recentOrders=[];this.editName='';this.editPhone='';this.currentPwd='';this.newPwd='';this.confirmPwd='';this.addressForm={label:'',name:'',phone:'',street:''};this.router.navigate(['/login']);}}

  activeTab = 'info';

  // --- Personal info ---
  editName = '';
  editPhone = '';
  infoError = '';
  savingInfo = false;

  // --- Avatar ---
  previewAvatar = '';
  avatarError = '';
  savingAvatar = false;
  private avatarTouched = false;
  private avatarReadId = 0;
  private destroyed = false;
  get avatarDirty(): boolean { return this.avatarTouched && this.previewAvatar !== (this.authService.currentUser?.avatar || ''); }
  ngOnDestroy(): void { this.destroyed = true; this.avatarReadId++; }

  // --- Addresses ---
  addresses: any[] = [];
  showAddressForm = false;
  editingAddressId: number | null = null;
  addressForm = {
    label: '',
    name: '',
    phone: '',
    street: ''
  };
  addressError = '';

  // --- Change password ---
  currentPwd = '';
  newPwd = '';
  confirmPwd = '';
  passwordError = '';

  // --- Statistics ---
  stats = {
    totalOrders: 0,
    totalSpent: 0,
    totalReviews: 0,
    cartCount: 0
  };

  // --- Order summary ---
  recentOrders: any[] = [];
  ordersLoading = false;
  ordersError = '';
  private ordersRequest = 0;

  constructor(
    public authService: AuthService,
    private orderService: OrderService,
    private cartService: CartService,
    private notification: NotificationService,
    private addressService: AddressService,
    private reviewService: ReviewService,
    private router: Router
  ) {}

  ngOnInit(): void {

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    const user = this.authService.currentUser;
    this.owner=user.token||user.email;

    this.editName = user.name || '';
    this.editPhone = user.phone || '';
    this.previewAvatar = user.avatar || '';

    this.loadAddresses();
    this.loadStats();
    this.loadRecentOrders();

  }

  setTab(tab: string): void {
    this.activeTab = tab === 'avatar' ? 'info' : tab;
  }

  // ===== PERSONAL INFO =====

  async saveInfo(): Promise<void> {
    if (this.savingInfo) return;
    try {

    this.infoError = '';

    if (!this.editName.trim()) {
      this.infoError = 'Vui lòng nhập họ và tên';
      return;
    }

    if (!this.editPhone.trim()) {
      this.infoError = 'Vui lòng nhập số điện thoại';
      return;
    }

    this.savingInfo = true;
    await this.authService.updateUser({
      name: this.editName.trim(),
      phone: this.editPhone.trim()
    });

    this.notification.success('Cập nhật thông tin thành công');

    } catch(error) { this.infoError=(error as any)?.error?.message || (error as any)?.message || 'Chưa lưu được thay đổi. Bạn thử lại nhé.'; this.notification.error(this.infoError); }
    finally { this.savingInfo = false; }
  }

  // ===== AVATAR =====

  onAvatarSelected(event: any): void {

    if (this.savingAvatar) return;
    const file: File = event.target.files && event.target.files[0];

    if (!file) {
      return;
    }

    this.avatarError = '';
    if(!['image/png','image/jpeg','image/gif','image/webp'].includes(file.type) || file.size>262144){this.avatarError='Bạn chọn ảnh PNG/JPEG/GIF/WebP tối đa 256 KB nhé.';this.notification.error(this.avatarError);return;}
    const reader = new FileReader();
    const readId = ++this.avatarReadId;
    const owner = this.authService.currentUser?.token || this.authService.currentUser?.email;

    reader.onload = () => {
      if (this.destroyed || readId !== this.avatarReadId || owner !== (this.authService.currentUser?.token || this.authService.currentUser?.email)) return;
      this.previewAvatar = reader.result as string;
      this.avatarTouched = true;
    };

    reader.onerror = () => { if (!this.destroyed && readId === this.avatarReadId) this.avatarError = 'Chưa đọc được ảnh. Bạn chọn ảnh khác nhé.'; };

    reader.readAsDataURL(file);
    event.target.value = '';

  }

  async saveAvatar(): Promise<void> {
    if (this.savingAvatar || !this.avatarDirty || !this.previewAvatar) return;
    try {
    this.savingAvatar = true;
    this.avatarError = '';
    await this.authService.updateUser({ avatar: this.previewAvatar });
    this.avatarTouched = false;
    this.notification.success('Cập nhật ảnh đại diện thành công');
    } catch(error) { this.avatarError=(error as any)?.error?.message || (error as any)?.message || 'Chưa lưu được ảnh. Bạn thử lại nhé.'; }
    finally { this.savingAvatar = false; }
  }

  cancelAvatar(): void {
    if (this.savingAvatar) return;
    this.avatarReadId++; this.avatarTouched = false; this.avatarError = '';
    this.previewAvatar = this.authService.currentUser?.avatar || '';
  }

  async removeAvatar(): Promise<void> {
    if (this.savingAvatar) return;
    try {
    this.savingAvatar = true; this.avatarError = '';
    await this.authService.updateUser({ avatar: '' });
    this.avatarReadId++; this.previewAvatar = ''; this.avatarTouched = false;
    this.notification.success('Đã xoá ảnh đại diện');
    } catch(error) { this.avatarError=(error as any)?.error?.message || (error as any)?.message || 'Chưa xoá được ảnh. Bạn thử lại nhé.'; }
    finally { this.savingAvatar = false; }
  }

  // ===== ADDRESSES =====

  private get email(): string {
    return this.authService.currentUser.email;
  }

  async loadAddresses(): Promise<void> {
    try { await this.addressService.refresh(this.email); this.addresses = this.addressService.getAddresses(this.email); if(!this.avatarTouched)this.previewAvatar=this.authService.currentUser?.avatar || '';const notice=localStorage.getItem('vinabook_preferences_import:'+encodeURIComponent(this.email)+':notice');if(notice)this.addressError=notice; }
    catch(error) { this.addressError=(error as any)?.error?.message || 'Chưa tải được dữ liệu đồng bộ. Bạn thử lại nhé.'; }
  }

  openAddAddress(): void {

    this.editingAddressId = null;
    this.addressForm = { label: '', name: '', phone: '', street: '' };
    this.addressError = '';
    this.showAddressForm = true;

  }

  editAddress(address: any): void {

    this.editingAddressId = address.id;
    this.addressForm = {
      label: address.label,
      name: address.name,
      phone: address.phone,
      street: address.street
    };
    this.addressError = '';
    this.showAddressForm = true;

  }

  cancelAddressForm(): void {
    this.showAddressForm = false;
    this.editingAddressId = null;
  }

  async saveAddress(): Promise<void> {
    try {

    this.addressError = shippingError(this.addressForm.name,this.addressForm.phone,this.addressForm.street);
    if(this.addressError)return;

    if (
      !this.addressForm.name.trim() ||
      !this.addressForm.phone.trim() ||
      !this.addressForm.street.trim()
    ) {
      this.addressError = 'Vui lòng nhập đầy đủ thông tin';
      return;
    }

    if (this.editingAddressId !== null) {
      await this.addressService.updateAddress(
        this.email,
        this.editingAddressId,
        this.addressForm
      );
    } else {
      await this.addressService.addAddress(this.email, this.addressForm);
    }

    this.loadAddresses();
    this.showAddressForm = false;
    this.editingAddressId = null;
    this.notification.success('Lưu địa chỉ thành công');

    } catch(error) { this.addressError=(error as any)?.error?.message || (error as any)?.message || 'Chưa lưu được thay đổi. Bạn thử lại nhé.'; this.notification.error(this.addressError); }
  }

  async deleteAddress(address: any): Promise<void> {
    try {

    await this.addressService.deleteAddress(this.email, address.id);
    this.loadAddresses();
    this.notification.success('Đã xoá địa chỉ');

    } catch(error) { this.addressError=(error as any)?.error?.message || (error as any)?.message || 'Chưa lưu được thay đổi. Bạn thử lại nhé.'; this.notification.error(this.addressError); }
  }

  async setDefaultAddress(address: any): Promise<void> {
    try {

    await this.addressService.setDefault(this.email, address.id);
    this.loadAddresses();

    } catch(error) { this.addressError=(error as any)?.error?.message || (error as any)?.message || 'Chưa lưu được thay đổi. Bạn thử lại nhé.'; this.notification.error(this.addressError); }
  }

  // ===== CHANGE PASSWORD =====

  changePassword(): void {

    this.passwordError = '';

    if (!this.currentPwd) {
      this.passwordError = 'Vui lòng nhập mật khẩu hiện tại';
      return;
    }

    if (this.newPwd.length < 6) {
      this.passwordError = 'Mật khẩu mới phải có ít nhất 6 ký tự';
      return;
    }

    if (this.newPwd !== this.confirmPwd) {
      this.passwordError = 'Mật khẩu xác nhận không khớp';
      return;
    }

    this.authService
      .changePassword(this.currentPwd, this.newPwd)
      .subscribe(result => {

        if (!result.success) {
          this.passwordError = result.error!;
          return;
        }

        this.currentPwd = '';
        this.newPwd = '';
        this.confirmPwd = '';

        this.notification.success('Đổi mật khẩu thành công');

      });

  }

  // ===== STATISTICS =====

  loadStats(): void {

    this.stats.totalReviews = this.countReviews();
    this.stats.cartCount = this.cartService.getCartCount();

    this.orderService.getOrderPage(0, 1).subscribe(page => {
      this.stats.totalOrders = page.counts['-1'] || 0;
      this.stats.totalSpent = page.totalSpent;
    });

  }

  private countReviews(): number {
    // Đếm số review của chính user này từ cache rates backend
    return this.reviewService.countByEmail(this.email);
  }

  // ===== ORDER SUMMARY =====

  loadRecentOrders(): void {
    const owner = this.authService.currentUser?.token || this.authService.currentUser?.email || '';
    const request = ++this.ordersRequest;
    this.ordersLoading = true;
    this.ordersError = '';
    const isCurrent = () => request === this.ordersRequest && owner ===
      (this.authService.currentUser?.token || this.authService.currentUser?.email || '');
    this.orderService.getOrderPage(0, 5).subscribe(page => {
      if (!isCurrent()) return;
      this.recentOrders = page.items;
      this.ordersLoading = false;
    }, () => {
      if (!isCurrent()) return;
      this.ordersLoading = false;
      this.ordersError = 'Chưa tải được đơn hàng. Bạn thử lại nhé.';
    });
  }

  orderBookCount(order: any): number {
    return (order.items || []).reduce((total: number, item: any) => total + Number(item.quantity || 0), 0);
  }

  trackOrder(_index: number, order: any): number { return order.id; }

  // ===== LOGOUT =====

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }

}

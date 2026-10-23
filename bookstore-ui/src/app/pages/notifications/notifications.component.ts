import { Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';
import { AutomationPreferences, AutomationService, CustomerNotice, NoticeType } from 'src/app/services/automation.service';
import { ProductService } from 'src/app/services/product.service';
import { RestockService, StockNotice } from 'src/app/services/restock.service';

interface NoticeBook { id: number; image: string; author: string; }
type NoticeView = 'all' | 'unread' | 'waiting';
type PreferenceKey = 'orders' | 'restock' | 'priceDrops' | 'cartPriceChanges' | 'cartReminders' | 'reviewReminders' | 'authorReleases';

@Component({ selector: 'app-notifications', templateUrl: './notifications.component.html', styleUrls: ['./notifications.component.scss'] })
export class NotificationsComponent implements OnInit, OnDestroy, DoCheck {
  notices: CustomerNotice[] = [];
  watches: StockNotice[] = [];
  view: NoticeView = 'all';
  type: NoticeType | 'ALL' = 'ALL';
  page = 0;
  total = 0;
  busy = false;
  loaded = false;
  watchLoaded = false;
  watchError = '';
  error = '';
  message = '';
  actionId = '';
  confirmId = '';
  updatedAt: Date | null = null;
  books: { [id: number]: NoticeBook } = {};
  showPreferences = false;
  preferences: AutomationPreferences | null = null;
  preferenceBusy = false;
  preferenceError = '';
  preferenceMessage = '';
  preferenceDirty = false;
  authors: string[] = [];
  author = '';
  authorQuery = '';
  private owner = '';
  private subscriptions = new Subscription();
  private request?: Subscription;
  private watchRequest?: Subscription;
  private preferencesRequest?: Subscription;
  private catalog?: Subscription;
  private release?: () => void;

  readonly kinds: { value: NoticeType | 'ALL'; label: string }[] = [
    { value: 'ALL', label: 'Mọi loại thông báo' }, { value: 'ORDER_STATUS', label: 'Đơn hàng' },
    { value: 'RESTOCK', label: 'Có hàng trở lại' }, { value: 'PRICE_DROP', label: 'Giảm giá' }, { value: 'CART_PRICE_CHANGE', label: 'Giá trong giỏ' },
    { value: 'CART_REMINDER', label: 'Nhắc giỏ hàng' }, { value: 'REVIEW_REMINDER', label: 'Mời đánh giá' },
    { value: 'AUTHOR_RELEASE', label: 'Sách mới của tác giả' }
  ];
  readonly preferenceOptions: { key: PreferenceKey; label: string; description: string }[] = [
    { key: 'orders', label: 'Tiến trình đơn hàng', description: 'Khi đơn hàng của bạn thay đổi trạng thái.' },
    { key: 'restock', label: 'Sách có hàng trở lại', description: 'Khi cuốn sách đã đăng ký theo dõi được nhập lại.' },
    { key: 'priceDrops', label: 'Sách yêu thích giảm giá', description: 'Khi giá bán của sách trong danh sách yêu thích giảm.' },
    { key: 'cartPriceChanges', label: 'Thay đổi giá sách trong giỏ', description: 'Khi giá sách đang trong giỏ của bạn tăng hoặc giảm. Chỉ gửi khi bạn bật lựa chọn này.' },
    { key: 'cartReminders', label: 'Nhắc giỏ hàng', description: 'Nhắc về những cuốn sách bạn đã chọn nhưng chưa mua.' },
    { key: 'reviewReminders', label: 'Mời đánh giá sau mua', description: 'Sau khi đơn giao thành công và bạn chưa đánh giá sách.' },
    { key: 'authorReleases', label: 'Sách mới của tác giả', description: 'Khi cửa hàng bổ sung sách của tác giả bạn theo dõi.' }
  ];

  constructor(private stock: RestockService, private auth: AuthService, private router: Router,
    private products: ProductService, public automation: AutomationService) {}

  get unreadCount(): number { return this.automation.unreadCount; }
  get totalPages(): number { return Math.ceil(this.total / 20); }
  get busyAction(): boolean { return !!this.actionId; }
  get accountEmail(): string { return this.auth.currentUser?.email || ''; }
  get validNotificationEmail(): boolean {
    const address=(this.preferences?.emailAddress || '').trim();
    return address.length<=254 && /^[^\s@<>(),;:"\\]+@[^\s@<>(),;:"\\]+\.[^\s@<>(),;:"\\]+$/.test(address);
  }
  useAccountEmail(): void {
    if(this.preferences && this.accountEmail && !this.preferenceBusy){this.preferences.emailAddress=this.accountEmail;this.preferenceChanged();}
  }
  get filteredAuthors(): string[] {
    const followed = new Set(this.preferences?.followedAuthors || []);
    const query = this.authorQuery.trim().toLocaleLowerCase('vi');
    return this.authors.filter(name => !followed.has(name) && (!query || name.toLocaleLowerCase('vi').includes(query)));
  }

  ngOnInit(): void {
    this.owner = this.auth.currentUser?.token || '';
    this.release = this.automation.connect();
    this.load();
    this.loadWatches();
    this.subscriptions.add(this.automation.refreshed$.subscribe(() => {
      if (!this.confirmId && !this.actionId && !this.preferenceBusy) { this.load(true); this.loadWatches(true); }
    }));
  }
  ngOnDestroy(): void {
    this.subscriptions.unsubscribe(); this.request?.unsubscribe(); this.watchRequest?.unsubscribe();
    this.preferencesRequest?.unsubscribe(); this.catalog?.unsubscribe(); this.release?.();
    this.owner = '';
  }
  ngDoCheck(): void {
    this.automation.syncSession();
    if (this.owner !== (this.auth.currentUser?.token || '')) {
      this.ngOnDestroy(); this.notices = []; this.watches = []; this.books = {}; this.preferences = null;
      this.message = ''; this.error = ''; this.preferenceMessage = ''; this.confirmId = '';
      this.router.navigate(['/login']);
    }
  }
  private current(key: string): boolean { return !!key && key === this.owner && key === this.auth.currentUser?.token; }

  load(silent = false): void {
    if (this.busy || !this.current(this.owner) || this.actionId) { return; }
    if (this.view === 'waiting') { this.loadWatches(silent); return; }
    this.busy = true; this.error = ''; if (!silent) { this.message = ''; }
    const key = this.owner;
    this.request = this.automation.inbox(this.page, this.type, this.view === 'unread').subscribe({
      next: result => {
        if (!this.current(key)) { return; }
        this.notices = result.items; this.total = result.total; this.busy = false; this.loaded = true; this.updatedAt = new Date();
        if (this.notices.length) { this.loadCovers(key); }
      },
      error: () => {
        if (!this.current(key)) { return; }
        this.busy = false;
        this.error = this.loaded ? 'Chưa làm mới được thông báo. Danh sách đang hiển thị là lần tải gần nhất.' : 'Chưa tải được thông báo. Bạn kiểm tra kết nối rồi thử lại nhé.';
      }
    });
  }

  loadWatches(silent = false): void {
    if (!this.current(this.owner) || this.actionId) { return; }
    if (this.watchRequest && !this.watchRequest.closed) { if (this.view === 'waiting') { this.busy = true; } return; }
    const key = this.owner; this.watchError = '';
    if (this.view === 'waiting') { this.busy = true; if (!silent) { this.message = ''; } }
    this.watchRequest = this.stock.list().subscribe({
      next: rows => {
        if (!this.current(key)) { return; }
        this.watches = rows; this.watchLoaded = true;
        if (this.view === 'waiting') { this.busy = false; this.updatedAt = new Date(); }
        if (rows.length) { this.loadCovers(key); }
      },
      error: () => {
        if (!this.current(key)) { return; }
        this.watchError = 'Chưa tải được sách theo dõi. Bạn thử lại nhé.';
        if (this.view === 'waiting') { this.busy = false; }
      }
    });
  }

  private loadCovers(key: string): void {
    if (this.catalog) { return; }
    this.catalog = this.products.getProducts().subscribe({
      next: books => {
        if (this.current(key)) { this.books = books.reduce((result: { [id: number]: NoticeBook }, book: NoticeBook) => { result[book.id] = book; return result; }, {}); }
      }, error: () => {}
    });
  }

  changeView(view: NoticeView): void {
    if (this.actionId) { return; }
    this.request?.unsubscribe(); this.busy = false; this.view = view; this.page = 0;
    this.confirmId = ''; this.error = ''; this.message = ''; this.notices = []; this.loaded = false;
    this.load();
  }
  changeType(): void { this.changeView(this.view === 'waiting' ? 'all' : this.view); }
  changePage(page: number): void {
    if (this.busy || this.actionId || page < 0 || page >= this.totalPages) { return; }
    this.page = page; this.loaded = false; this.notices = []; this.confirmId = ''; this.load();
  }
  label(type: NoticeType): string { return this.kinds.find(kind => kind.value === type)?.label || 'Thông báo'; }
  icon(type: NoticeType): string {
    return type === 'PRICE_DROP' ? 'tag' : type === 'ORDER_STATUS' ? 'truck' : type === 'CART_REMINDER' || type === 'CART_PRICE_CHANGE' ? 'shopping-cart' : type === 'REVIEW_REMINDER' ? 'message-circle' : type === 'AUTHOR_RELEASE' ? 'book-open' : 'bell';
  }
  noticeLink(row: CustomerNotice): string {
    // Generate destinations from typed IDs; never trust a server-supplied external or script URL.
    if ((row.type === 'ORDER_STATUS') && Number.isSafeInteger(row.orderId) && row.orderId! > 0) { return '/orders/' + row.orderId; }
    if (row.type === 'CART_REMINDER' || row.type === 'CART_PRICE_CHANGE') { return '/cart'; }
    if (Number.isSafeInteger(row.productId) && row.productId! > 0) { return '/product/' + row.productId; }
    return row.type === 'ORDER_STATUS' ? '/orders' : '/books';
  }
  actionLabel(row: CustomerNotice): string { return row.type === 'ORDER_STATUS' ? 'Xem đơn hàng' : row.type === 'CART_REMINDER' || row.type === 'CART_PRICE_CHANGE' ? 'Xem giỏ hàng' : row.type === 'REVIEW_REMINDER' ? 'Đánh giá sách' : 'Xem sách'; }
  trackNotice(_index: number, row: CustomerNotice): string { return row.id; }
  trackWatch(_index: number, row: StockNotice): number { return row.productId; }

  markRead(row: CustomerNotice): void { if (!row.seen && !this.busy && !this.actionId && this.current(this.owner)) { this.perform(row.id, this.automation.seen(row.id), 'Đã đánh dấu thông báo là đã đọc.'); } }
  markAllRead(): void { if (this.unreadCount && !this.busy && !this.actionId && this.current(this.owner)) { this.perform('all', this.automation.seenAll(), 'Đã đánh dấu tất cả thông báo là đã đọc.'); } }
  requestRemoval(id: string): void { if (!this.busy && !this.actionId) { this.confirmId = id; this.message = ''; } }
  remove(row: CustomerNotice): void { if (this.confirmId === row.id && !this.busy && !this.actionId && this.current(this.owner)) { this.perform(row.id, this.automation.remove(row.id), 'Đã xóa thông báo.', true); } }
  removeWatch(row: StockNotice): void {
    const id = 'watch-' + row.productId;
    if (this.confirmId === id && !this.busy && !this.actionId && this.current(this.owner)) { this.perform(id, this.stock.remove(row.productId), 'Đã bỏ theo dõi cuốn sách này.', true); }
  }
  private perform(id: string, operation: Observable<void>, success: string, removing = false): void {
    if (this.busy || this.actionId || !this.current(this.owner)) { return; }
    const key = this.owner; this.actionId = id; this.error = ''; this.message = '';
    this.subscriptions.add(operation.subscribe({
      next: () => {
        if (!this.current(key)) { return; }
        this.actionId = ''; this.confirmId = ''; this.message = success;
        if (this.view === 'waiting') { this.watches = this.watches.filter(row => 'watch-' + row.productId !== id); }
        else { if (this.notices.length === 1 && this.page > 0 && (this.view === 'unread' || removing)) { this.page--; } this.load(true); }
      },
      error: () => { if (this.current(key)) { this.actionId = ''; this.error = 'Chưa cập nhật được thông báo. Bạn thử lại nhé.'; } }
    }));
  }

  togglePreferences(): void {
    this.showPreferences = !this.showPreferences;
    if (this.showPreferences && !this.preferences && !this.preferenceBusy) { this.loadPreferences(); }
  }
  loadPreferences(): void {
    if (!this.current(this.owner) || this.preferenceBusy) { return; }
    const key = this.owner; this.preferenceBusy = true; this.preferenceError = '';
    this.preferencesRequest = this.automation.preferences().subscribe({
      next: value => {
        if (!this.current(key)) { return; }
        this.preferences = { ...value, emailAddress: value.emailAddress || this.accountEmail, followedAuthors: [...value.followedAuthors] }; this.preferenceBusy = false; this.preferenceDirty = false;
      }, error: () => { if (this.current(key)) { this.preferenceBusy = false; this.preferenceError = 'Chưa tải được tùy chọn thông báo. Bạn thử lại nhé.'; } }
    });
    this.subscriptions.add(this.automation.authors().subscribe({ next: names => { if (this.current(key)) { this.authors = names; } }, error: () => {} }));
  }
  preferenceChanged(): void { this.preferenceDirty = true; this.preferenceMessage = ''; this.preferenceError = ''; }
  followAuthor(): void {
    if (!this.preferences || this.preferenceBusy || !this.author || !this.authors.includes(this.author) || this.preferences.followedAuthors.includes(this.author)) { return; }
    this.preferences.followedAuthors = [...this.preferences.followedAuthors, this.author]; this.author = ''; this.preferenceChanged();
  }
  unfollowAuthor(name: string): void {
    if (!this.preferences || this.preferenceBusy) { return; }
    this.preferences.followedAuthors = this.preferences.followedAuthors.filter(author => author !== name); this.preferenceChanged();
  }
  savePreferences(): void {
    if (!this.preferences || !this.preferenceDirty || this.preferenceBusy || !this.current(this.owner)) { return; }
    if(this.preferences.emailEnabled && !this.validNotificationEmail){this.preferenceError='Bạn nhập một địa chỉ email nhận thông báo hợp lệ nhé.';return;}
    const settings={...this.preferences,emailAddress:(this.preferences.emailAddress || '').trim()};
    const key = this.owner; this.preferenceBusy = true; this.preferenceError = ''; this.preferenceMessage = '';
    this.preferencesRequest = this.automation.savePreferences(settings).subscribe({
      next: value => { if (this.current(key)) { this.preferences = { ...value, emailAddress: value.emailAddress || this.accountEmail, followedAuthors: [...value.followedAuthors] }; this.preferenceBusy = false; this.preferenceDirty = false; this.preferenceMessage = value.emailEnabled && value.emailAvailable===false ? 'Đã lưu email nhận tin. Cửa hàng sẽ gửi thông báo khi dịch vụ email sẵn sàng.' : 'Đã lưu tùy chọn thông báo.'; } },
      error: () => { if (this.current(key)) { this.preferenceBusy = false; this.preferenceError = 'Chưa lưu được tùy chọn. Thay đổi của bạn vẫn ở đây để thử lại.'; } }
    });
  }
}

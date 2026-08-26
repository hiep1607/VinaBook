import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { HeaderComponent } from 'src/app/components/header/header.component';
import { AuthService } from 'src/app/services/auth.service';
import { CartService } from 'src/app/services/cart.service';
import { CategoryService } from 'src/app/services/category.service';
import { ProductService } from 'src/app/services/product.service';
import { SearchHistoryService } from 'src/app/services/search-history.service';
import { SearchService } from 'src/app/services/search.service';
import { WishlistService } from 'src/app/services/wishlist.service';

import { SearchComponent } from './search.component';

/** Catalog giả lập — "tam" khớp đúng 1 kết quả. */
const CATALOG = [
  { id: 1, name: 'Tam Quốc Diễn Nghĩa', author: 'La Quán Trung' },
  { id: 2, name: 'Nhật Ký Trong Tù', author: 'Hồ Chí Minh' },
  { id: 3, name: 'Dế Mèn Phiêu Lưu Ký', author: 'Tô Hoài' }
];

describe('SearchComponent — chuẩn hóa query canonical `q`', () => {
  let fixture: ComponentFixture<SearchComponent>;
  let component: SearchComponent;
  let routerSpy: jasmine.SpyObj<Router>;
  let searchSvc: { search: jasmine.Spy; getCategories: jasmine.Spy };
  let historySvc: { addToHistory: jasmine.Spy };
  let queryParams$: BehaviorSubject<Params>;

  beforeEach(() => {
    queryParams$ = new BehaviorSubject<Params>({});
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    searchSvc = {
      search: jasmine.createSpy('search').and.callFake((kw: string) =>
        kw
          ? CATALOG.filter(book => book.name.toLowerCase().indexOf(kw.toLowerCase()) !== -1)
          : CATALOG.slice()
      ),
      getCategories: jasmine.createSpy('getCategories').and.returnValue([])
    };
    historySvc = { addToHistory: jasmine.createSpy('addToHistory') };

    const productSvc = {
      getProducts: jasmine.createSpy('getProducts').and.returnValue(of(CATALOG.slice())),
      refresh: jasmine.createSpy('refresh')
    };

    TestBed.configureTestingModule({
      declarations: [SearchComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams$.asObservable(),
            snapshot: { queryParams: {} }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: SearchService, useValue: searchSvc },
        { provide: SearchHistoryService, useValue: historySvc },
        { provide: ProductService, useValue: productSvc }
      ]
    });
  });

  /** Khởi tạo component với một bộ query params (giả lập F5 thẳng vào URL). */
  function init(params: Params): void {
    queryParams$.next(params);
    fixture = TestBed.createComponent(SearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('đọc q=tam làm keyword canonical và tìm đúng kết quả', () => {
    init({ q: 'tam' });

    expect(component.keyword).toBe('tam');
    expect(searchSvc.search).toHaveBeenCalledWith('tam', '');
    // Chỉ "Tam Quốc Diễn Nghĩa" chứa "tam".
    expect(component.products.length).toBe(1);
    expect(component.products[0].name).toBe('Tam Quốc Diễn Nghĩa');
    expect(historySvc.addToHistory).toHaveBeenCalledWith('tam');
  });

  it('chuyển URL cũ keyword=tam sang q=tam bằng replaceUrl', () => {
    init({ keyword: 'tam' });

    expect(routerSpy.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        replaceUrl: true,
        queryParams: { q: 'tam' }
      })
    );

    // Sau khi URL được rewrite, subscription nhận params mới và chạy search.
    queryParams$.next({ q: 'tam' });
    expect(component.keyword).toBe('tam');
    expect(searchSvc.search).toHaveBeenCalledWith('tam', '');
  });

  it('q rỗng không gây lỗi và trả về toàn bộ catalog', () => {
    init({ q: '' });

    expect(component.loading).toBe(false);
    expect(component.loadError).toBe(false);
    expect(component.keyword).toBe('');
    expect(searchSvc.search).toHaveBeenCalledWith('', '');
    expect(component.products.length).toBe(CATALOG.length);
  });

  it('đổi category giữ nguyên q hiện tại, không tái sinh keyword, về trang 1', () => {
    init({ q: 'tam', category: '' });
    component.setCategory('Văn học');

    const args = routerSpy.navigate.calls.mostRecent().args;
    expect(args[0]).toEqual(['/search']);
    const config = args[1] as any;
    expect(config.queryParamsHandling).toBe('merge');
    expect(config.queryParams).toEqual({ category: 'Văn học', page: 1 });
    expect(Object.prototype.hasOwnProperty.call(config.queryParams, 'keyword')).toBe(false);
  });
});

describe('SearchComponent — phân trang và bộ lọc (P1)', () => {
  let fixture: ComponentFixture<SearchComponent>;
  let component: SearchComponent;
  let routerSpy: jasmine.SpyObj<Router>;
  let searchSvc: { search: jasmine.Spy; getCategories: jasmine.Spy };
  let historySvc: { addToHistory: jasmine.Spy };
  let queryParams$: BehaviorSubject<Params>;

  /** Catalog 25 sản phẩm giá tăng dần → 3 trang với pageSize 12. */
  const BIG_CATALOG = Array.from({ length: 25 }, (_, i) => ({
    id: i + 1,
    name: 'Sách ' + (i + 1),
    price: (i + 1) * 10000,
    oldPrice: i % 2 === 0 ? (i + 1) * 12000 : 0,
    discount: i % 2 === 0 ? 10 : 0,
    quantity: i === 24 ? 0 : 5,
    category: ''
  }));

  beforeEach(() => {
    queryParams$ = new BehaviorSubject<Params>({});
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    searchSvc = {
      search: jasmine.createSpy('search').and.returnValue(BIG_CATALOG.slice()),
      getCategories: jasmine.createSpy('getCategories').and.returnValue([])
    };
    historySvc = { addToHistory: jasmine.createSpy('addToHistory') };

    const productSvc = {
      getProducts: jasmine.createSpy('getProducts').and.returnValue(of(BIG_CATALOG.slice())),
      refresh: jasmine.createSpy('refresh')
    };

    TestBed.configureTestingModule({
      declarations: [SearchComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams$.asObservable(),
            snapshot: { queryParams: {} }
          }
        },
        { provide: Router, useValue: routerSpy },
        { provide: SearchService, useValue: searchSvc },
        { provide: SearchHistoryService, useValue: historySvc },
        { provide: ProductService, useValue: productSvc }
      ]
    });
  });

  function init(params: Params): void {
    queryParams$.next(params);
    fixture = TestBed.createComponent(SearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('không render quá pageSize sản phẩm trong grid', () => {
    init({});

    expect(component.products.length).toBe(25);
    expect(component.totalPages).toBe(3);
    expect(component.pagedProducts.length).toBe(12);
  });

  it('đọc page từ URL khi F5 thẳng vào /search?page=2', () => {
    init({ page: '2' });

    // BehaviorSubject phát đồng bộ ngay khi subscription được thiết lập
    expect(component.page).toBe(2);
    expect(component.pagedProducts.length).toBe(12);
    expect(component.pagedProducts[0].id).toBe(13);
  });

  it('goToPage điều hướng kèm param page', () => {
    init({});
    component.goToPage(3);

    const config = routerSpy.navigate.calls.mostRecent().args[1] as any;
    expect(config.queryParamsHandling).toBe('merge');
    expect(config.queryParams).toEqual({ page: 3 });
  });

  it('sort giá tăng/giảm đồng bộ URL và về trang 1', () => {
    init({ page: '2' });
    queryParams$.next({ page: '2' });
    component.setSortOrder('price-asc');

    const config = routerSpy.navigate.calls.mostRecent().args[1] as any;
    expect(config.queryParams).toEqual({ sort: 'price-asc', page: 1 });

    // Sau khi URL cập nhật, kết quả được sắp xếp theo giá tăng dần
    queryParams$.next({ sort: 'price-asc' });
    expect(component.filteredProducts[0].price).toBe(10000);
  });

  it('lọc "Đang khuyến mãi" chỉ giữ sản phẩm có giảm giá', () => {
    init({});
    component.togglePromo();

    const config = routerSpy.navigate.calls.mostRecent().args[1] as any;
    expect(config.queryParams).toEqual({ promo: '1', page: 1 });

    queryParams$.next({ promo: '1' });
    expect(component.promoOnly).toBe(true);
    expect(component.filteredProducts.every(p => p.discount > 0)).toBe(true);
  });

  it('lọc "Còn hàng" loại sản phẩm hết hàng', () => {
    init({});
    component.toggleInStock();

    queryParams$.next({ stock: '1' });
    expect(component.inStockOnly).toBe(true);
    expect(component.filteredProducts.every(p => Number(p.quantity) > 0)).toBe(true);
    // Sách 25 có quantity = 0 → bị loại
    expect(component.filteredProducts.some(p => p.id === 25)).toBe(false);
  });

  it('khoảng giá lọc theo min/max từ URL', () => {
    init({ minPrice: 150000, maxPrice: 200000 });

    expect(component.minPrice).toBe(150000);
    expect(component.maxPrice).toBe(200000);
    expect(component.filteredProducts.every(p =>
      p.price >= 150000 && p.price <= 200000
    )).toBe(true);
  });

  it('clearAllFilters gỡ toàn bộ filter trên URL', () => {
    init({ category: 'Văn học', promo: '1', stock: '1', minPrice: 1000, maxPrice: 2000, sort: 'price-asc', page: '2' });
    component.clearAllFilters();

    const config = routerSpy.navigate.calls.mostRecent().args[1] as any;
    expect(config.queryParams).toEqual({
      category: '',
      promo: null,
      stock: null,
      minPrice: null,
      maxPrice: null,
      sort: null,
      page: 1
    });
  });
});

describe('HeaderComponent — điều hướng tìm kiếm bằng query `q`', () => {
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    // Ivy render template ngay khi createComponent -> RouterLinkWithHref cần events.
    (routerSpy as any).events = of();

    TestBed.configureTestingModule({
      declarations: [HeaderComponent],
      // Template dùng <lucide-icon> từ thư viện; với test này chỉ cần logic TS.
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: Router, useValue: routerSpy },
        {
          provide: CartService,
          useValue: { getCartCount: () => 0, changes$: of() }
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: () => false, currentUser: { name: '', email: '' } }
        },
        { provide: WishlistService, useValue: { getCount: () => 0 } },
        { provide: SearchService, useValue: {} },
        {
          provide: SearchHistoryService,
          useValue: { addToHistory: jasmine.createSpy('addToHistory') }
        },
        { provide: CategoryService, useValue: { getAll: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: { queryParams: new BehaviorSubject<Params>({}).asObservable() }
        }
      ]
    });
  });

  it('search() điều hướng /search?q=..., không dùng keyword', () => {
    const headerFixture = TestBed.createComponent(HeaderComponent);
    const header = headerFixture.componentInstance;

    header.keyword = '  tam  ';
    header.search();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/search'], {
      queryParams: { q: 'tam' }
    });
    const config = routerSpy.navigate.calls.mostRecent().args[1] as any;
    expect(Object.prototype.hasOwnProperty.call(config.queryParams, 'keyword')).toBe(false);
  });

  it('search() không điều hướng khi từ khóa rỗng', () => {
    const headerFixture = TestBed.createComponent(HeaderComponent);
    const header = headerFixture.componentInstance;

    header.keyword = '   ';
    header.search();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});

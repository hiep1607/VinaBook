import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { SearchHistoryService } from 'src/app/services/search-history.service';
import { SearchService } from 'src/app/services/search.service';
import { ProductService } from 'src/app/services/product.service';

type SortOrder = 'default' | 'price-asc' | 'price-desc';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.scss']
})
export class SearchComponent implements OnInit, OnDestroy {

  keyword = '';
  activeCategory = '';
  sortOrder: SortOrder = 'default';
  /** Trang hiện tại (1-based), đồng bộ query param `page`. */
  page = 1;
  readonly pageSize = 12;

  /** Bộ lọc bổ sung trên dữ liệu thật của catalog (discount/quantity/price). */
  promoOnly = false;
  inStockOnly = false;
  minPrice: number | null = null;
  maxPrice: number | null = null;

  /** Panel bộ lọc thu gọn trên mobile. */
  filterPanelOpen = false;

  products: any[] = [];

  loading = true;
  loadError = false;

  readonly skeletonItems = [1, 2, 3, 4, 5, 6, 7, 8];

  private paramsSub!: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    public searchService: SearchService,
    private searchHistory: SearchHistoryService,
    private productService: ProductService
  ) {}

  ngOnInit(): void {
    this.loadCatalog();
  }

  // Đợi catalog từ API sẵn sàng rồi mới chạy search —
  // tránh kết quả rỗng khi F5 thẳng vào /search
  private loadCatalog(): void {
    this.loading = true;
    this.loadError = false;

    this.productService.getProducts().subscribe(
      () => {
        this.loading = false;
        if (this.paramsSub) {
          this.paramsSub.unsubscribe();
        }
        this.paramsSub = this.route.queryParams.subscribe(params => {
          // `q` là query canonical; `keyword` chỉ còn đọc lại cho link cũ.
          const q = typeof params['q'] === 'string' ? params['q'] : '';
          const legacyKeyword = typeof params['keyword'] === 'string' ? params['keyword'] : '';

          // Chuẩn hóa URL cũ `?keyword=...` sang `?q=...` bằng replaceUrl
          // (không tạo thêm entry history nên không phá back/forward).
          if (legacyKeyword) {
            const next: any = { ...params, q: q || legacyKeyword };
            delete next['keyword'];
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: next,
              replaceUrl: true
            });
            return; // queryParams phát lại sau điều hướng sẽ chạy nhánh chính
          }

          this.keyword        = q;
          this.activeCategory = params['category'] || '';
          this.sortOrder      = this.parseSort(params['sort']);
          this.promoOnly      = params['promo'] === '1';
          this.inStockOnly    = params['stock'] === '1';
          this.minPrice       = this.parsePrice(params['minPrice']);
          this.maxPrice       = this.parsePrice(params['maxPrice']);
          this.page           = Math.max(1, parseInt(params['page'], 10) || 1);
          this.runSearch();
        });
      },
      () => {
        this.loading = false;
        this.loadError = true;
      }
    );
  }

  retry(): void {
    this.productService.refresh();
    this.loadCatalog();
  }

  ngOnDestroy(): void {
    if (this.paramsSub) {
      this.paramsSub.unsubscribe();
    }
  }

  private runSearch(): void {
    this.products = this.searchService.search(this.keyword, this.activeCategory);
    if (this.keyword.trim()) {
      this.searchHistory.addToHistory(this.keyword.trim());
    }
  }

  private parseSort(value: any): SortOrder {
    return value === 'price-asc' || value === 'price-desc' ? value : 'default';
  }

  private parsePrice(value: any): number | null {
    const num = Number(value);
    return value !== '' && value !== null && value !== undefined && !Number.isNaN(num) && num >= 0
      ? num
      : null;
  }

  /**
   * Kết quả sau lọc + sắp xếp — nguồn duy nhất cho pagination và đếm số lượng.
   */
  get filteredProducts(): any[] {
    let list = [...this.products];

    if (this.promoOnly) {
      list = list.filter(p => p.discount > 0 || Number(p.oldPrice) > Number(p.price));
    }

    if (this.inStockOnly) {
      list = list.filter(p => Number(p.quantity) > 0);
    }

    if (this.minPrice !== null) {
      list = list.filter(p => Number(p.price) >= this.minPrice!);
    }

    if (this.maxPrice !== null) {
      list = list.filter(p => Number(p.price) <= this.maxPrice!);
    }

    if (this.sortOrder === 'price-asc') {
      list.sort((a, b) => Number(a.price) - Number(b.price));
    } else if (this.sortOrder === 'price-desc') {
      list.sort((a, b) => Number(b.price) - Number(a.price));
    }

    return list;
  }

  get sortedProducts(): any[] {
    // Toàn bộ kết quả đã lọc + sort (dùng cho đếm số lượng).
    return this.filteredProducts;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredProducts.length / this.pageSize));
  }

  /** Chỉ tối đa `pageSize` sản phẩm được render trong grid kết quả. */
  get pagedProducts(): any[] {
    const safePage = Math.min(this.page, this.totalPages);
    return this.filteredProducts.slice((safePage - 1) * this.pageSize, safePage * this.pageSize);
  }

  get categories(): string[] {
    return this.searchService.getCategories();
  }

  get hasActiveFilters(): boolean {
    return !!this.activeCategory || this.promoOnly || this.inStockOnly
      || this.minPrice !== null || this.maxPrice !== null;
  }

  /** Điều hướng merge vào URL; param rỗng/null sẽ bị gỡ khỏi URL. */
  private updateParams(changes: any): void {
    this.router.navigate(['/search'], {
      queryParams: changes,
      queryParamsHandling: 'merge'
    });
  }

  setCategory(cat: string): void {
    // merge giữ nguyên query hiện tại (`q`) trên URL khi đổi category/sort;
    // mọi thay đổi filter/query đều đưa về trang 1.
    this.updateParams({ category: cat, page: 1 });
  }

  setSortOrder(order: SortOrder): void {
    this.updateParams({ sort: order === 'default' ? null : order, page: 1 });
  }

  togglePromo(): void {
    this.updateParams({ promo: this.promoOnly ? null : '1', page: 1 });
  }

  toggleInStock(): void {
    this.updateParams({ stock: this.inStockOnly ? null : '1', page: 1 });
  }

  applyPriceRange(min: any, max: any): void {
    this.updateParams({
      minPrice: min !== null && min !== '' ? Number(min) : null,
      maxPrice: max !== null && max !== '' ? Number(max) : null,
      page: 1
    });
  }

  clearFilter(key: 'category' | 'promo' | 'stock' | 'price'): void {
    if (key === 'category') {
      this.updateParams({ category: '', page: 1 });
    } else if (key === 'promo') {
      this.updateParams({ promo: null, page: 1 });
    } else if (key === 'stock') {
      this.updateParams({ stock: null, page: 1 });
    } else {
      this.updateParams({ minPrice: null, maxPrice: null, page: 1 });
    }
  }

  clearAllFilters(): void {
    this.updateParams({
      category: '',
      promo: null,
      stock: null,
      minPrice: null,
      maxPrice: null,
      sort: null,
      page: 1
    });
  }

  goToPage(target: number): void {
    const clamped = Math.min(Math.max(1, target), this.totalPages);
    if (clamped === this.page) {
      return;
    }
    this.updateParams({ page: clamped });
  }

  get priceInputsInvalid(): boolean {
    return this.minPrice !== null && this.maxPrice !== null && this.minPrice > this.maxPrice;
  }

  /** Số filter đang áp dụng (hiển thị trên nút Bộ lọc ở mobile). */
  activeFilterCount(): number {
    let count = 0;
    if (this.activeCategory) { count++; }
    if (this.promoOnly) { count++; }
    if (this.inStockOnly) { count++; }
    if (this.minPrice !== null || this.maxPrice !== null) { count++; }
    return count;
  }

  /** Danh sách trang cho pagination. */
  totalPagesArray(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

}

import {
  AfterViewInit,
  Directive,
  ElementRef,
  Input,
  OnDestroy,
  Renderer2
} from '@angular/core';

/**
 * Hiệu ứng "reveal khi cuộn" — phần tử mờ + dịch chuyển nhẹ, khi lọt vào
 * khung nhìn thì hiện ra mượt mà (như các trang landing cao cấp).
 *
 * Dùng IntersectionObserver nên rất nhẹ, không nghe scroll thủ công.
 *
 * Cách dùng:
 *   <div appReveal>...</div>                  // trượt lên mặc định
 *   <div appReveal="left">...</div>           // trượt từ trái
 *   <div appReveal="scale" [revealDelay]="120">...</div>  // phóng to, trễ 120ms
 *
 * Hướng hỗ trợ: 'up' (mặc định) | 'down' | 'left' | 'right' | 'scale'.
 * Style tương ứng nằm trong styles.scss (.reveal / .reveal--*).
 */
@Directive({
  selector: '[appReveal]'
})
export class RevealDirective implements AfterViewInit, OnDestroy {

  /** Hướng xuất hiện. */
  @Input('appReveal') from: '' | 'up' | 'down' | 'left' | 'right' | 'scale' = 'up';

  /** Độ trễ (ms) — dùng để tạo hiệu ứng so le (stagger) giữa các phần tử. */
  @Input() revealDelay = 0;

  private observer?: IntersectionObserver;

  constructor(
    private el: ElementRef<HTMLElement>,
    private renderer: Renderer2
  ) {}

  ngAfterViewInit(): void {
    const host = this.el.nativeElement;
    const dir = this.from || 'up';

    this.renderer.addClass(host, 'reveal');
    this.renderer.addClass(host, `reveal--${dir}`);

    if (this.revealDelay) {
      this.renderer.setStyle(host, 'transition-delay', `${this.revealDelay}ms`);
    }

    // Trình duyệt cũ / SSR không có IntersectionObserver → hiện luôn.
    if (typeof IntersectionObserver === 'undefined') {
      this.renderer.addClass(host, 'reveal--in');
      return;
    }

    this.observer = new IntersectionObserver(
      entries => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            this.renderer.addClass(host, 'reveal--in');
            this.observer?.unobserve(host);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
    );

    this.observer.observe(host);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}

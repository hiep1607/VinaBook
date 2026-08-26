import { Injectable } from '@angular/core';

/**
 * Micro-interaction: tạo "ghost" bìa sách bay từ vị trí click
 * tới icon giỏ hàng trên header (#header-cart-btn).
 *
 * - Dùng Web Animations API, không phụ thuộc thư viện ngoài.
 * - Tôn trọng prefers-reduced-motion: người dùng tắt hiệu ứng thì bỏ qua.
 * - Không có icon giỏ hàng trên màn hình thì im lặng bỏ qua (mobile menu...).
 */
@Injectable({ providedIn: 'root' })
export class FlyToCartService {

  private get reducedMotion(): boolean {
    return typeof window !== 'undefined'
      && !!window.matchMedia
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /** source: phần tử ảnh bìa sách xuất phát. An toàn khi gọi với null. */
  fly(source: HTMLElement | null | undefined): void {
    if (!source || this.reducedMotion) {
      return;
    }

    const target = document.getElementById('header-cart-btn');
    const img = source.matches('img') ? source as HTMLImageElement : source.querySelector('img');
    if (!target || !img) {
      return;
    }

    const from = source.getBoundingClientRect();
    const to = target.getBoundingClientRect();
    if (!from.width || !to.width) {
      return;
    }

    const startX = from.left + from.width / 2 - 28;  // giữa ghost 56px
    const startY = from.top + from.height / 2 - 36;  // giữa ghost 72px
    const deltaX = (to.left + to.width / 2) - (startX + 28);
    const deltaY = (to.top + to.height / 2) - (startY + 36);

    const ghost = document.createElement('div');
    ghost.className = 'fly-to-cart-ghost';
    ghost.style.left = `${startX}px`;
    ghost.style.top = `${startY}px`;
    const src = img.currentSrc || img.src;
    if (src) {
      ghost.style.backgroundImage = `url("${src}")`;
    }
    document.body.appendChild(ghost);

    const animation = ghost.animate(
      [
        { transform: 'translate(0, 0) scale(1) rotate(0deg)', opacity: 1 },
        { transform: `translate(${deltaX * 0.5}px, ${deltaY * 0.5 - 60}px) scale(0.6) rotate(-8deg)`, opacity: 0.95, offset: 0.55 },
        { transform: `translate(${deltaX}px, ${deltaY}px) scale(0.18) rotate(6deg)`, opacity: 0.35 }
      ],
      { duration: 680, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' }
    );

    const cleanup = () => { ghost.remove(); };
    animation.onfinish = cleanup;
    animation.oncancel = cleanup;

    // Nhấn nhẹ icon giỏ hàng khi ghost chạm tới
    setTimeout(() => {
      target.classList.add('cart-landed');
      setTimeout(() => target.classList.remove('cart-landed'), 400);
    }, 600);
  }

}

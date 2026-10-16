import { Component, Input } from '@angular/core';

export interface OrderItemView {
  name: string; quantity: number; image?: string; price?: number; productId?: number | null;
}

/** Order prices are saved line prices, never a current catalog quote. */
@Component({ selector: 'app-order-items', templateUrl: './order-items.component.html', styleUrls: ['./order-items.component.scss'] })
export class OrderItemsComponent {
  @Input() items: OrderItemView[] = [];
  @Input() compact = false;
  readonly failedImages = new Set<string>();
  hasPrice(item: OrderItemView): boolean { return typeof item.price === 'number' && Number.isFinite(item.price); }
}

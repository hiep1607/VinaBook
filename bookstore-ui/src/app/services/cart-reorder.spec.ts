import { CartService } from './cart.service';
describe('Bulk cart changes for reorder', () => {
  const key = 'cart_reorder@test.invalid'; let saved: string | null; let cart: CartService;
  beforeEach(() => { saved = localStorage.getItem(key); localStorage.setItem(key, '[]'); cart = new CartService({ currentUser: { email: 'reorder@test.invalid' } } as any); });
  afterEach(() => { cart.ngOnDestroy(); if (saved === null) localStorage.removeItem(key); else localStorage.setItem(key, saved); });
  it('validates every line before changing any item', () => {
    expect(() => cart.addBooks([{ product: { id: 1, name: 'A', quantity: 5 }, quantity: 1 }, { product: { id: 2, name: 'B', quantity: 0 }, quantity: 1 }])).toThrow();
    expect(cart.getCartItems()).toEqual([]);
  });
  it('keeps existing books and adds requested quantities with one save', () => {
    cart.addToCart({ id: 1, name: 'A', quantity: 5 }, 1); const save = spyOn(cart, 'saveCart').and.callThrough();
    cart.addBooks([{ product: { id: 1, name: 'A', price: 120000, quantity: 5 }, quantity: 2 }, { product: { id: 2, name: 'B', quantity: 5 }, quantity: 1 }]);
    expect(cart.getCartItems().map(item => item.quantity)).toEqual([3, 1]); expect(save).toHaveBeenCalledTimes(1);
  });
});

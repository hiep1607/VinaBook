import { FormBuilder } from '@angular/forms';
import { fakeAsync, tick } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';
import { HomepageComponent } from './homepage.component';
import { DEFAULT_HOMEPAGE_CONFIG } from '../../common/HomepageConfig';

describe('HomepageComponent loading', () => {
  let component: HomepageComponent;
  let homepage: any;
  let products: any;
  let categories: any;
  let toast: any;

  beforeEach(() => {
    homepage = { getConfig: jasmine.createSpy().and.returnValue(of(DEFAULT_HOMEPAGE_CONFIG)), updateConfig: jasmine.createSpy() };
    products = { getAll: jasmine.createSpy().and.returnValue(of([{productId: 1, status: true}, {productId: 2, status: false}])) };
    categories = { getAll: jasmine.createSpy().and.returnValue(of([{categoryId: 1, status: true}])) };
    toast = { error: jasmine.createSpy(), success: jasmine.createSpy(), warning: jasmine.createSpy() };
    component = new HomepageComponent(new FormBuilder(), homepage, products, categories, {setPageActive: () => {}} as any, toast);
  });

  afterEach(() => component.ngOnDestroy());

  it('loads the saved config and available catalog', () => {
    component.ngOnInit();
    expect(component.loadError).toBeFalse();
    expect(component.loading).toBeFalse();
    expect(component.products.map(book => book.productId)).toEqual([1]);
    expect(component.form.get('hero.title')!.value).toBe(DEFAULT_HOMEPAGE_CONFIG.hero.title);
  });

  it('clears a failed load and opens the editor after retry succeeds', () => {
    products.getAll.and.returnValues(throwError({status: 0}), of([]));
    component.ngOnInit();
    expect(component.loadError).toBeTrue();
    expect(component.loadErrorTitle).toContain('danh sách sách');
    component.retryLoad();
    expect(component.loadError).toBeFalse();
    expect(component.loadErrorMessage).toBe('');
    expect(component.loading).toBeFalse();
    expect(products.getAll).toHaveBeenCalledTimes(2);
  });

  it('explains invalid sessions and blocks saving the fallback form', () => {
    products.getAll.and.returnValue(throwError({status: 401}));
    component.ngOnInit();
    expect(component.sessionError).toBeTrue();
    expect(component.loadErrorMessage).toContain('đăng nhập lại');
    component.form.markAsDirty(); component.save();
    expect(homepage.updateConfig).not.toHaveBeenCalled();
  });

  it('distinguishes permission denial from a backend connection failure', () => {
    products.getAll.and.returnValue(throwError({status: 403}));
    component.ngOnInit();
    expect(component.sessionError).toBeFalse();
    expect(component.loadErrorTitle).toBe('Không có quyền tải dữ liệu');
    expect(component.loadErrorMessage).toContain('danh sách sách');
  });

  it('stops indefinite loading and ignores extra retry clicks', fakeAsync(() => {
    products.getAll.and.returnValue(NEVER);
    component.ngOnInit(); component.retryLoad();
    expect(products.getAll).toHaveBeenCalledTimes(1);
    tick(15000);
    expect(component.loading).toBeFalse();
    expect(component.loadError).toBeTrue();
  }));
});

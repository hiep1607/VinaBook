import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Product } from '../common/Product';
import { environment } from 'src/environments/environment';
import { ProductPreviewManifest, ProductPreviewPage } from '../common/ProductPreview';
import { BookProfileInput } from '../common/BookKnowledge';

@Injectable({
  providedIn: 'root'
})
export class ProductService {

  url = environment.apiUrl + "/api/products";

  constructor(private httpClient: HttpClient) { }

  getAll() {
    return this.httpClient.get(`${environment.apiUrl}/api/admin/products`);
  }

  getKnowledge(id: number) {
    return this.httpClient.get<BookProfileInput>(`${environment.apiUrl}/api/admin/books/${id}/profile`);
  }

  suggestKnowledge(id: number) {
    return this.httpClient.post<BookProfileInput>(`${environment.apiUrl}/api/admin/books/${id}/suggest`, {});
  }

  publishKnowledge(id: number, profile: BookProfileInput) {
    return this.httpClient.put<BookProfileInput>(`${environment.apiUrl}/api/admin/books/${id}/publish`, profile);
  }

  reindexKnowledge() {
    return this.httpClient.post<{configured: boolean; indexed: number; remaining: number; failedProductId?: number}>(
      `${environment.apiUrl}/api/admin/books/reindex`, {});
  }

  getOne(id: number) {
    return this.httpClient.get(`${environment.apiUrl}/api/admin/products/${id}`);
  }

  getBestSeller() {
    return this.httpClient.get(this.url + '/bestseller-admin');
  }

  save(product: Product) {
    return this.httpClient.post(this.url, product);
  }

  update(product: Product, id: number) {
    return this.httpClient.put(this.url + '/' + id, product);
  }

  delete(id: number) {
    return this.httpClient.delete(this.url + '/' + id);
  }

  getPreview(productId: number) {
    return this.httpClient.get<ProductPreviewManifest>(`${environment.apiUrl}/api/admin/products/${productId}/preview`);
  }

  addPreviewPage(productId: number, page: ProductPreviewPage) {
    return this.httpClient.post<ProductPreviewPage>(`${environment.apiUrl}/api/admin/products/${productId}/preview/pages`, page);
  }

  updatePreviewPage(productId: number, pageId: number, page: ProductPreviewPage) {
    return this.httpClient.put<ProductPreviewPage>(`${environment.apiUrl}/api/admin/products/${productId}/preview/pages/${pageId}`, page);
  }

  updatePreviewSettings(productId: number, enabled: boolean, rightsConfirmed: boolean) {
    return this.httpClient.put<ProductPreviewManifest>(`${environment.apiUrl}/api/admin/products/${productId}/preview`, {
      enabled,
      rightsConfirmed
    });
  }

  deletePreviewPage(productId: number, pageId: number) {
    return this.httpClient.delete(`${environment.apiUrl}/api/admin/products/${productId}/preview/pages/${pageId}`);
  }

  reorderPreviewPages(productId: number, pageIds: number[]) {
    return this.httpClient.put<ProductPreviewManifest>(`${environment.apiUrl}/api/admin/products/${productId}/preview/reorder`, pageIds);
  }
}

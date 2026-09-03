export interface ProductPreviewPage {
  previewPageId?: number;
  pageOrder: number;
  originalPageNumber?: number;
  imageUrl: string;
  enabled?: boolean;
}

export interface ProductPreviewManifest {
  productId: number;
  enabled: boolean;
  rightsConfirmed?: boolean;
  pageCount: number;
  pages: ProductPreviewPage[];
}

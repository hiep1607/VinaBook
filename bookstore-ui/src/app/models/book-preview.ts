export interface BookPreviewPage {
  previewPageId?: number;
  pageOrder: number;
  originalPageNumber?: number;
  imageUrl: string;
  enabled?: boolean;
}

export interface BookPreviewManifest {
  productId: number;
  enabled: boolean;
  rightsConfirmed?: boolean;
  pageCount: number;
  pages: BookPreviewPage[];
}

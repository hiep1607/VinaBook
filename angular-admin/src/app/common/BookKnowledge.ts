export interface BookRelationInput {
  targetProductId: number;
  type: 'SAME_SERIES' | 'NEXT_VOLUME' | 'READ_BEFORE';
  evidence: string;
}

export interface BookProfileInput {
  productId: number;
  published: boolean;
  semanticReady: boolean;
  summary: string;
  audience: string;
  readingLevel: string;
  readingStyle: string;
  learningGoal: string;
  language: string;
  publisher: string;
  publicationYear?: number;
  pageCount?: number;
  isbn: string;
  sourceNote: string;
  topics: string[];
  relations: BookRelationInput[];
}

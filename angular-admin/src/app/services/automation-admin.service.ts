import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

export type AutomationRuleKey = 'RESTOCK' | 'PRICE_DROP' | 'CART_PRICE_CHANGE' | 'ORDER_STATUS' | 'CART_REMINDER' | 'REVIEW_REMINDER' | 'AUTHOR_RELEASE' | 'LOW_STOCK';
export interface AutomationRule {
  key: AutomationRuleKey;
  label: string;
  description: string;
  enabled: boolean;
  delayHours: number | null;
  threshold: number | null;
}
export interface AutomationStockRow {
  productId: number;
  name: string;
  quantity: number;
  threshold: number;
  waitingReaders: number;
}
export interface AutomationDemandRow {
  productId: number;
  name: string;
  waitingReaders: number;
  quantity: number;
}
export interface AutomationRun {
  id: string;
  startedAt: string;
  status: string;
  createdCount: number;
  error: string | null;
}
export interface AutomationDashboard {
  rules: AutomationRule[];
  stats: { unreadLowStock: number; waitingReaders: number; notificationsToday: number; failedDeliveries: number };
  lowStock: AutomationStockRow[];
  demand: AutomationDemandRow[];
  recentRuns: AutomationRun[];
}
export interface CatalogDemandReport {
  days: number;
  items: { title: string; requestCount: number; lastRequestedAt: string }[];
}

@Injectable({ providedIn: 'root' })
export class AutomationAdminService {
  private readonly api = environment.apiUrl + '/api/admin/automations';
  constructor(private http: HttpClient) {}

  get(): Observable<AutomationDashboard> {
    return this.http.get<AutomationDashboard>(this.api).pipe(timeout(20000));
  }
  save(rule: AutomationRule): Observable<AutomationRule> {
    return this.http.put<AutomationRule>(this.api + '/rules/' + encodeURIComponent(rule.key), rule).pipe(timeout(20000));
  }
  run(): Observable<AutomationDashboard> {
    return this.http.post<AutomationDashboard>(this.api + '/run', {}).pipe(timeout(60000));
  }
  catalogDemand(): Observable<CatalogDemandReport> {
    return this.http.get<CatalogDemandReport>(environment.apiUrl + '/api/admin/catalog-demand').pipe(timeout(20000));
  }
}

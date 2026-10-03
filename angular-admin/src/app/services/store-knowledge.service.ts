import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from 'src/environments/environment';

export interface StoreSettings {
  shippingFee: number;
  freeShippingThreshold: number;
  deliveryMinDays: number;
  deliveryMaxDays: number;
  updatedAt?: string;
}
export interface StorePolicy {
  id?: number;
  policyKey: string;
  version?: number;
  title: string;
  body: string;
  effectiveAt?: string;
  updatedAt?: string;
  approved?: boolean;
}
export interface StoreKnowledge {
  settings: StoreSettings;
  policies: StorePolicy[];
  paymentMethods: { [key: string]: { label: string; available: boolean } };
  history: StorePolicy[];
}

@Injectable({ providedIn: 'root' })
export class StoreKnowledgeService {
  private url = `${environment.apiUrl}/api/admin/store-knowledge`;
  constructor(private http: HttpClient) { }
  get() { return this.http.get<StoreKnowledge>(this.url); }
  saveSettings(settings: StoreSettings) { return this.http.put<StoreSettings>(`${this.url}/settings`, settings); }
  publishPolicy(policy: StorePolicy) {
    return this.http.put<StorePolicy>(`${this.url}/policies/${policy.policyKey}`, policy);
  }
}

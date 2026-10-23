import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

export type PaymentMethodCode = 'COD' | 'BANK_QR' | 'VNPAY' | 'MOMO';

export interface PaymentMethodInfo {
  available: boolean;
  label: string;
  description: string;
}

export interface PaymentState {
  orderId: number;
  amount: number;
  paymentMethod: PaymentMethodCode;
  paymentStatus: 'UNPAID' | 'PENDING' | 'PAID' | 'FAILED';
  orderStatus: number;
  paymentReference?: string;
  paymentTransactionId?: string;
  paidAt?: string;
  methodAvailable?: boolean;
  canStart?: boolean;
  canPoll?: boolean;
  redirectUrl?: string;
  qrUrl?: string;
  bankId?: string;
  bankAccountNo?: string;
  bankAccountName?: string;
  transferContent?: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {

  private api = environment.apiUrl + '/api/payments';

  constructor(private http: HttpClient) { }

  getMethods(): Observable<Partial<Record<PaymentMethodCode, PaymentMethodInfo>>> {
    return this.http.get<Partial<Record<PaymentMethodCode, PaymentMethodInfo>>>(this.api + '/methods');
  }

  start(orderId: number): Observable<PaymentState> {
    return this.http.post<PaymentState>(this.api + '/orders/' + orderId + '/start', {});
  }

  getStatus(orderId: number): Observable<PaymentState> {
    return this.http.get<PaymentState>(this.api + '/orders/' + orderId + '/status');
  }
}

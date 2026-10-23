import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, EMPTY, Observable, Subject, Subscription, of, timer } from 'rxjs';
import { filter, takeUntil, tap, timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { AuthService } from './auth.service';

export type NoticeType = 'RESTOCK' | 'PRICE_DROP' | 'CART_PRICE_CHANGE' | 'ORDER_STATUS' | 'CART_REMINDER' | 'REVIEW_REMINDER' | 'AUTHOR_RELEASE';
export interface CustomerNotice {
  id: string; type: NoticeType; title: string; message: string; link: string;
  productId: number | null; orderId: number | null; seen: boolean; createdAt: string;
}
export interface NoticePage { items: CustomerNotice[]; total: number; unreadCount: number; }
export interface AutomationPreferences {
  restock: boolean; priceDrops: boolean; cartPriceChanges?: boolean; orders: boolean; cartReminders: boolean;
  reviewReminders: boolean; authorReleases: boolean; emailEnabled: boolean; followedAuthors: string[]; emailAvailable?: boolean; emailAddress?: string;
}

/** One session-aware poller shared by the header and the inbox. No guest requests or cross-account cache. */
@Injectable({ providedIn: 'root' })
export class AutomationService {
  private readonly api = environment.apiUrl + '/api/automations/me';
  private readonly count = new BehaviorSubject<number>(0);
  private readonly sessionChanged = new Subject<void>();
  private readonly refreshed = new Subject<void>();
  readonly unreadCount$ = this.count.asObservable();
  readonly refreshed$ = this.refreshed.asObservable();
  private owner = '';
  private clients = 0;
  private poll?: Subscription;
  private summary?: Subscription;
  private summaryVersion = 0;

  constructor(private http: HttpClient, private auth: AuthService) {}
  get unreadCount(): number { return this.count.value; }

  connect(): () => void {
    this.clients++;
    this.syncSession();
    if (!this.poll) {
      this.poll = timer(60000, 60000).subscribe(() => {
        this.syncSession();
        if (this.owner) { this.refreshSummary(true); }
      });
    }
    let released = false;
    return () => {
      if (released) { return; }
      released = true;
      if (--this.clients === 0) {
        this.poll?.unsubscribe(); this.poll = undefined;
        this.summary?.unsubscribe(); this.summary = undefined;
      }
    };
  }

  syncSession(): void {
    const key = this.auth.currentUser?.token || '';
    if (key === this.owner) { return; }
    this.owner = key;
    this.sessionChanged.next();
    this.summary?.unsubscribe(); this.summary = undefined;
    this.summaryVersion++;
    this.count.next(0);
    if (key && this.clients) { this.refreshSummary(); }
  }

  private scoped<T>(request: Observable<T>): Observable<T> {
    const key = this.owner;
    return request.pipe(timeout(15000), takeUntil(this.sessionChanged),
      filter(() => !!key && key === this.owner && key === this.auth.currentUser?.token));
  }

  refreshSummary(notify = false): void {
    this.syncSession();
    if (!this.owner) { return; }
    this.summary?.unsubscribe();
    const version = ++this.summaryVersion;
    this.summary = this.scoped(this.http.get<{ unreadCount: number }>(this.api + '/summary')).subscribe({
      next: result => {
        if (version !== this.summaryVersion) { return; }
        this.count.next(Math.max(0, result.unreadCount || 0));
        if (notify) { this.refreshed.next(); }
      },
      error: () => {} // Keep the last successful count; the inbox owns visible error/retry states.
    });
  }

  inbox(page = 0, type: NoticeType | 'ALL' = 'ALL', unread = false): Observable<NoticePage> {
    this.syncSession();
    if (!this.owner) { return of({ items: [], total: 0, unreadCount: 0 }); }
    const params = new HttpParams().set('page', String(page)).set('size', '20').set('type', type).set('unread', String(unread));
    const version = ++this.summaryVersion;
    return this.scoped(this.http.get<NoticePage>(this.api + '/inbox', { params })).pipe(tap(result => {
      if (version === this.summaryVersion) { this.count.next(Math.max(0, result.unreadCount || 0)); }
    }));
  }

  seen(id: string): Observable<void> { return this.write(this.http.put<void>(this.api + '/inbox/' + encodeURIComponent(id) + '/seen', {})); }
  seenAll(): Observable<void> { return this.write(this.http.put<void>(this.api + '/inbox/seen', {})); }
  remove(id: string): Observable<void> { return this.write(this.http.delete<void>(this.api + '/inbox/' + encodeURIComponent(id))); }
  private write(request: Observable<void>): Observable<void> {
    this.syncSession();
    if (!this.owner) { return EMPTY; }
    this.summaryVersion++;
    return this.scoped(request).pipe(tap(() => this.refreshSummary()));
  }

  preferences(): Observable<AutomationPreferences> {
    this.syncSession();
    if (!this.owner) { return EMPTY; }
    return this.scoped(this.http.get<AutomationPreferences>(this.api + '/preferences'));
  }
  savePreferences(value: AutomationPreferences): Observable<AutomationPreferences> {
    this.syncSession();
    if (!this.owner) { return EMPTY; }
    const { emailAvailable, ...settings } = value;
    return this.scoped(this.http.put<AutomationPreferences>(this.api + '/preferences', settings));
  }
  authors(): Observable<string[]> {
    this.syncSession();
    if (!this.owner) { return EMPTY; }
    return this.scoped(this.http.get<string[]>(this.api + '/authors'));
  }
}

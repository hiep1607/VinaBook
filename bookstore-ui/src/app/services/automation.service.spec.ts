import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { AutomationService } from './automation.service';

describe('AutomationService session and shared polling', () => {
  let service: AutomationService;
  let http: HttpTestingController;
  let auth: any;
  let releases: (() => void)[];
  beforeEach(() => {
    auth = { currentUser: { token: 'ownerA' } }; releases = [];
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule], providers: [{ provide: AuthService, useValue: auth }] });
    service = TestBed.inject(AutomationService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { releases.forEach(release => release()); http.verify({ ignoreCancelled: true }); });
  const summary = () => http.expectOne(request => request.url.endsWith('/api/automations/me/summary'));
  const connect = () => { const release = service.connect(); releases.push(release); return release; };

  it('makes no polling, preference or author requests as a guest', fakeAsync(() => {
    auth.currentUser = null; const release = connect(); service.preferences().subscribe(); service.authors().subscribe();
    tick(120000); http.expectNone(() => true); expect(service.unreadCount).toBe(0); release();
  }));
  it('shares one timer between header and inbox and releases it after the last client', fakeAsync(() => {
    const first = connect(); summary().flush({ unreadCount: 2 });
    const second = connect(); http.expectNone(() => true);
    tick(60000); summary().flush({ unreadCount: 3 }); expect(service.unreadCount).toBe(3);
    first(); tick(60000); summary().flush({ unreadCount: 4 });
    second(); tick(60000); http.expectNone(() => true);
  }));
  it('cancels old requests and immediately clears a previous account badge', () => {
    connect(); summary().flush({ unreadCount: 8 });
    service.refreshSummary(); const old = summary();
    auth.currentUser = { token: 'ownerB' }; service.syncSession();
    expect(old.cancelled).toBeTrue(); expect(service.unreadCount).toBe(0);
    summary().flush({ unreadCount: 1 }); expect(service.unreadCount).toBe(1);
    auth.currentUser = null; service.syncSession(); expect(service.unreadCount).toBe(0);
  });
  it('does not expose an inbox response after the actual auth account has changed', () => {
    connect(); summary().flush({ unreadCount: 0 }); const delivered = jasmine.createSpy();
    service.inbox().subscribe(delivered);
    const request = http.expectOne(value => value.url.endsWith('/inbox'));
    auth.currentUser = { token: 'ownerB' };
    request.flush({ items: [{ id: 'private' }], total: 1, unreadCount: 1 });
    expect(delivered).not.toHaveBeenCalled(); expect(service.unreadCount).toBe(0);
  });
  it('prevents a late old summary from overwriting a newer inbox count', () => {
    connect(); const old = summary();
    service.inbox(2, 'PRICE_DROP', true).subscribe();
    const inbox = http.expectOne(value => value.url.endsWith('/inbox'));
    expect(inbox.request.params.get('page')).toBe('2'); expect(inbox.request.params.get('size')).toBe('20');
    expect(inbox.request.params.get('type')).toBe('PRICE_DROP'); expect(inbox.request.params.get('unread')).toBe('true');
    old.flush({ unreadCount: 20 }); expect(service.unreadCount).toBe(0);
    inbox.flush({ items: [], total: 0, unreadCount: 2 }); expect(service.unreadCount).toBe(2);
  });
  it('refreshes the shared count after a successful read mutation', () => {
    connect(); summary().flush({ unreadCount: 2 });
    service.seen('notice-7').subscribe();
    const write = http.expectOne(value => value.url.endsWith('/inbox/notice-7/seen'));
    expect(write.request.method).toBe('PUT'); write.flush(null);
    summary().flush({ unreadCount: 1 }); expect(service.unreadCount).toBe(1);
  });
  it('does not pretend a failed mutation succeeded or clear its unread badge', () => {
    connect(); summary().flush({ unreadCount: 2 }); const failed = jasmine.createSpy();
    service.seenAll().subscribe({ error: failed });
    http.expectOne(value => value.url.endsWith('/inbox/seen')).flush({}, { status: 500, statusText: 'Server error' });
    expect(failed).toHaveBeenCalled(); expect(service.unreadCount).toBe(2); http.expectNone(() => true);
  });
  it('sends chosen preferences without copying the readonly mail availability flag', () => {
    connect(); summary().flush({ unreadCount: 0 });
    service.savePreferences({ restock: true, priceDrops: true, orders: true, cartReminders: false, reviewReminders: false, authorReleases: true, emailEnabled: false, emailAvailable: false, emailAddress: 'chosen@example.com', followedAuthors: ['Tô Hoài'] }).subscribe();
    const write = http.expectOne(value => value.url.endsWith('/preferences'));
    expect(write.request.method).toBe('PUT'); expect(write.request.body.emailAvailable).toBeUndefined();
    expect(write.request.body.emailAddress).toBe('chosen@example.com');
    expect(write.request.body.emailEnabled).toBeFalse(); expect(write.request.body.followedAuthors).toEqual(['Tô Hoài']); write.flush(write.request.body);
  });
});

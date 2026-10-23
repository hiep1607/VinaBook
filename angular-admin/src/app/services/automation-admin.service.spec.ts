import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { environment } from 'src/environments/environment';
import { AutomationAdminService, AutomationRule } from './automation-admin.service';

describe('Automation admin API', () => {
  let service: AutomationAdminService;
  let http: HttpTestingController;
  const api = environment.apiUrl + '/api/admin/automations';
  beforeEach(() => { TestBed.configureTestingModule({ imports: [HttpClientTestingModule] }); service = TestBed.inject(AutomationAdminService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());

  it('loads the protected admin summary without owner IDs supplied by the client', () => {
    service.get().subscribe();
    const request = http.expectOne(api);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({ rules: [] });
  });
  it('sends one rule to its explicit endpoint without mutating the input', () => {
    const rule: AutomationRule = { key: 'LOW_STOCK', label: 'Tồn kho', description: 'Nhắc bổ sung', enabled: true, delayHours: null, threshold: 3 };
    service.save(rule).subscribe();
    const request = http.expectOne(api + '/rules/LOW_STOCK');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(rule);
    request.flush(rule);
  });
  it('uses the in-app scan endpoint without sending email options', () => {
    service.run().subscribe();
    const request = http.expectOne(api + '/run');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ rules: [] });
  });
  it('loads the separate missing-title report without coupling the automation summary', () => {
    service.catalogDemand().subscribe();
    const request = http.expectOne(environment.apiUrl + '/api/admin/catalog-demand');
    expect(request.request.method).toBe('GET');
    request.flush({ days: 30, items: [] });
  });
  it('bounds a dashboard request and cancels it on timeout', fakeAsync(() => {
    let failed = false;
    service.get().subscribe({ error: () => failed = true });
    const request = http.expectOne(api);
    tick(20001);
    expect(failed).toBeTrue();
    expect(request.cancelled).toBeTrue();
  }));
});

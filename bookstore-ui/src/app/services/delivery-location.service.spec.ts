import { HTTP_INTERCEPTORS, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { DeliveryLocationService } from './delivery-location.service';

describe('Delivery location external APIs', () => {
  let service: DeliveryLocationService, http: HttpTestingController;
  const position = { coords: { latitude: 21.0285, longitude: 105.8542, accuracy: 35 } } as GeolocationPosition;
  beforeEach(() => {
    const auth: HttpInterceptor = {intercept: (req: HttpRequest<any>, next: HttpHandler) => next.handle(req.clone({setHeaders: {Authorization: 'Bearer private-session'}}))};
    TestBed.configureTestingModule({imports: [HttpClientTestingModule], providers: [DeliveryLocationService,
      {provide: HTTP_INTERCEPTORS, useValue: auth, multi: true}]});
    service = TestBed.inject(DeliveryLocationService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('caches the province list and never sends the application JWT to third parties', fakeAsync(() => {
    let rows: any[] = []; service.provinces().then(value => rows = value);
    const req = http.expectOne('https://provinces.open-api.vn/api/v2/p/');
    expect(req.request.headers.has('Authorization')).toBeFalse(); expect(req.request.withCredentials).toBeFalse();
    req.flush([{code: 1, name: 'Thành phố Hà Nội'}]); flushMicrotasks(); service.provinces(); flushMicrotasks();
    http.expectNone('https://provinces.open-api.vn/api/v2/p/'); expect(rows.length).toBe(1);
  }));
  it('only loads wards for the selected province and validates parentage', fakeAsync(() => {
    let rows: any[] = []; service.wards(1).then(value => rows = value);
    const req = http.expectOne('https://provinces.open-api.vn/api/v2/p/1?depth=2');
    req.flush({wards: [{code: 4, name: 'Phường Ba Đình', province_code: 1}, {code: 99, name: 'Sai tỉnh', province_code: 79}]}); flushMicrotasks();
    expect(rows.map(row => row.code)).toEqual([4]);
  }));
  it('does not cache an area failure so the user can retry', fakeAsync(() => {
    service.provinces().catch(() => {}); http.expectOne('https://provinces.open-api.vn/api/v2/p/').flush({}, {status: 503, statusText: 'Unavailable'}); flushMicrotasks();
    let rows: any[] = []; service.provinces().then(value => rows = value); http.expectOne('https://provinces.open-api.vn/api/v2/p/').flush([{code: 1, name: 'Hà Nội'}]); flushMicrotasks();
    expect(rows[0].name).toBe('Hà Nội');
  }));
  it('builds a suggestion without assuming the nearby POI house number is the user address', fakeAsync(() => {
    let result: any; service.reverse(position).then(value => result = value);
    const req = http.expectOne(request => request.url === 'https://photon.komoot.io/reverse');
    expect(req.request.params.get('lat')).toBe('21.0285'); expect(req.request.headers.has('Authorization')).toBeFalse();
    expect(req.request.params.get('lang')).toBe('default');
    req.flush({features: [{properties: {countrycode: 'VN', housenumber: '17', name: 'Toà nhà bên cạnh', street: 'Phố A', city: 'Hà Nội'}}]}); flushMicrotasks();
    expect(result.street).toBe('Phố A, Hà Nội'); expect(result.accuracy).toBe(35);
  }));
  it('uses Vietnamese administrative names and deduplicates foreign variants without changing street names', fakeAsync(() => {
    service.provinces();
    http.expectOne('https://provinces.open-api.vn/api/v2/p/').flush([{code: 1, name: 'Thành phố Hà Nội'}]); flushMicrotasks();
    let result: any; service.reverse(position).then(value => result = value);
    http.expectOne(request => request.url.includes('/reverse')).flush({features: [{properties: {
      countrycode: 'VN', street: 'Hanoi Tower', district: 'Tây Tựu', city: 'Hanoï', state: 'Hanoi'
    }}]}); flushMicrotasks();
    expect(result.street).toBe('Hanoi Tower, Tây Tựu, Hà Nội');
  }));
  it('rejects locations outside Vietnam and empty geocoding results', fakeAsync(() => {
    let error = ''; service.reverse(position).catch(reason => error = reason.message);
    http.expectOne(request => request.url.includes('/reverse')).flush({features: [{properties: {countrycode: 'TH', city: 'Bangkok'}}]}); flushMicrotasks();
    expect(error).toContain('Việt Nam');
  }));
  it('bounds API wait time and leaves the caller able to use manual input', fakeAsync(() => {
    let error: any; service.reverse(position).catch(reason => error = reason);
    const req = http.expectOne(request => request.url.includes('/reverse'));
    tick(12001); flushMicrotasks(); expect(error).toBeDefined(); expect(req.cancelled).toBeTrue();
  }));
  it('bounds unanswered permission requests and ignores a late device result', fakeAsync(() => {
    let resolvePosition: PositionCallback = () => {};
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake(success => resolvePosition = success);
    let error = '', resolved = false; service.position().then(() => resolved = true, reason => error = reason.message);
    tick(18001); flushMicrotasks(); resolvePosition(position); flushMicrotasks();
    expect(error).toContain('Chưa nhận'); expect(resolved).toBeFalse();
  }));
});

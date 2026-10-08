import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule,HttpTestingController } from '@angular/common/http/testing';
import { CustomerPreferencesService } from './customer-preferences.service';
describe('CustomerPreferencesService server receipts',()=>{
  let service:CustomerPreferencesService;let http:HttpTestingController;
  const initial={addresses:[],avatar:'',wishlistProductIds:[7],revision:1};
  beforeEach(()=>{localStorage.clear();localStorage.setItem('currentUser',JSON.stringify({email:'a@example.com',token:'first'}));TestBed.configureTestingModule({imports:[HttpClientTestingModule]});service=TestBed.inject(CustomerPreferencesService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>{http.verify();localStorage.clear();});
  async function ready(){const promise=service.refresh('a@example.com');http.expectOne(req=>req.url.endsWith('/migrate')).flush(initial);await promise;}
  it('backs up old values and imports with a stable key',async()=>{localStorage.setItem('wishlist_a@example.com','[7]');const pending=service.refresh('a@example.com');const request=http.expectOne(req=>req.url.endsWith('/migrate'));expect(request.request.body.wishlistProductIds).toEqual([7]);const key=request.request.body.migrationKey;request.flush(initial);await pending;expect(localStorage.getItem('vinabook_preferences_import:a%40example.com')).toBe(key);expect(localStorage.getItem('vinabook_preferences_import:a%40example.com:backup')).toContain('[7]');});
  it('refresh reads server state after import',async()=>{await ready();const pending=service.refresh('a@example.com',true);http.expectOne(req=>req.method==='GET').flush({...initial,wishlistProductIds:[8]});await pending;expect(service.cached('a@example.com').wishlistProductIds).toEqual([8]);});
  it('does not report failed save as success',async()=>{await ready();const pending=service.mutate('a@example.com','wishlist',8);await new Promise(resolve=>setTimeout(resolve,0));http.expectOne(req=>req.method==='PUT').flush({message:'Offline'},{status:503,statusText:'Unavailable'});await expectAsync(pending).toBeRejected();expect(service.cached('a@example.com').wishlistProductIds).toEqual([7]);});
  it('does not install old account response after switch',async()=>{const pending=service.refresh('a@example.com');const req=http.expectOne(req=>req.url.endsWith('/migrate'));localStorage.setItem('currentUser',JSON.stringify({email:'b@example.com',token:'second'}));req.flush(initial);await expectAsync(pending).toBeRejected();expect(localStorage.getItem('wishlist_b@example.com')).toBeNull();});
  it('does not repeatedly fetch during change detection when offline',async()=>{const pending=service.refresh('a@example.com');http.expectOne(req=>req.url.endsWith('/migrate')).flush({},{status:503,statusText:'Unavailable'});await expectAsync(pending).toBeRejected();service.cached('a@example.com');service.cached('a@example.com');http.expectNone(req=>req.url.includes('/preferences/'));});
});

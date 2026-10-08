import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SupportChatComponent } from './support-chat.component';
describe('SupportChatComponent delivery and privacy',()=>{
  let component:SupportChatComponent;let http:HttpTestingController;let auth:any;
  beforeEach(()=>{TestBed.configureTestingModule({imports:[HttpClientTestingModule]});http=TestBed.inject(HttpTestingController);auth={currentUser:{token:'owner-token'}};component=new SupportChatComponent(TestBed.inject(HttpClient),auth);component.requestId=7;});
  afterEach(()=>{component.ngOnDestroy();http.verify({ignoreCancelled:true});});
  function start(){component.ngOnChanges();tick();http.expectOne(req=>req.method==='GET').flush([]);}
  const message={id:1,content:'Xin chào',staff:false,mine:true,createdAt:'2026-10-08T00:00:00Z'};
  it('polls only the selected ticket with cursor',fakeAsync(()=>{start();tick(3000);const req=http.expectOne(req=>req.method==='GET');expect(req.request.url).toContain('/me/7/messages');req.flush([message]);expect(component.messages.length).toBe(1);component.ngOnDestroy();}));
  it('keeps same delivery key after uncertain response',fakeAsync(()=>{start();component.draft='Xin chào';component.send();const first=http.expectOne(req=>req.method==='POST');const key=first.request.body.clientKey;first.flush({}, {status:503,statusText:'Unavailable'});expect(component.uncertain).toBeTrue();component.send();const retry=http.expectOne(req=>req.method==='POST');expect(retry.request.body.clientKey).toBe(key);retry.flush(message);expect(component.messages.length).toBe(1);expect(component.draft).toBe('');component.ngOnDestroy();}));
  it('never sends empty messages or into closed ticket',fakeAsync(()=>{start();component.draft=' ';component.send();http.expectNone(req=>req.method==='POST');component.draft='Xin chào';component.closed=true;component.send();http.expectNone(req=>req.method==='POST');component.ngOnDestroy();}));
  it('clears data and stops polling when account changes',fakeAsync(()=>{start();component.messages=[message];auth.currentUser.token='other-token';component.ngDoCheck();tick(6000);expect(component.messages).toEqual([]);http.expectNone(req=>req.method==='GET');component.ngOnDestroy();}));
  it('destroys previous poll before changing ticket',fakeAsync(()=>{start();component.requestId=8;component.ngOnChanges();tick();const req=http.expectOne(req=>req.method==='GET');expect(req.request.url).toContain('/me/8/messages');req.flush([]);component.ngOnDestroy();tick(6000);http.expectNone(req=>req.method==='GET');}));
});

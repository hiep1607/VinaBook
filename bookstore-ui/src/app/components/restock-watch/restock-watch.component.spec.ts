import {of,Subject} from 'rxjs';import {RestockWatchComponent} from './restock-watch.component';
describe('Restock watch',()=>{let c:RestockWatchComponent;let auth:any;let service:any;const row={productId:7,name:'Sách',state:'NONE',seen:false,availableAt:null,inStock:false};
 beforeEach(()=>{auth={currentUser:{token:'ownerA'}};service={get:jasmine.createSpy().and.returnValue(of(row)),watch:jasmine.createSpy().and.returnValue(of({...row,state:'WAITING'})),remove:jasmine.createSpy().and.returnValue(of(undefined))};c=new RestockWatchComponent(auth,service);c.productId=7;c.ngOnChanges();});afterEach(()=>c.ngOnDestroy());
 it('requires login to subscribe',()=>{auth.currentUser=null;c.ngDoCheck();c.toggle();expect(service.watch).not.toHaveBeenCalled();expect(c.data).toBeUndefined();});
 it('subscribes to chosen ID and explains where notification appears',()=>{c.toggle();expect(service.watch).toHaveBeenCalledOnceWith(7);expect(c.notice).toContain('Thông báo');});
 it('repeated clicks share only one pending write',()=>{service.watch.and.returnValue(new Subject());c.toggle();c.toggle();expect(service.watch).toHaveBeenCalledTimes(1);expect(c.busy).toBeTrue();});
 it('uses cancellation for a waiting subscription',()=>{c.data={...row,state:'WAITING'};c.toggle();expect(service.remove).toHaveBeenCalledOnceWith(7);expect(service.watch).not.toHaveBeenCalled();});
 it('clears private status on account change and ignores old response',()=>{const reply=new Subject<any>();service.get.and.returnValue(reply);c.load();auth.currentUser=null;c.ngDoCheck();reply.next({...row,state:'WAITING'});expect(c.data).toBeUndefined();expect(c.notice).toBe('');});
});

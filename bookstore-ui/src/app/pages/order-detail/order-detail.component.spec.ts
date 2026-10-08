import { convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
describe('OrderDetailComponent ownership and loading', () => {
  let component: OrderDetailComponent; let params: BehaviorSubject<any>; let service: any; let auth: any; let router: any;
  const order: any = {id:7, statusCode:0, status:'Chờ xử lý', total:120000, paymentMethod:'COD', paymentStatus:'UNPAID', items:[{price:50000,quantity:2}]};
  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({id:'7'}));
    service = {getOrder:jasmine.createSpy().and.returnValue(of(order))}; auth={isLoggedIn:()=>true,currentUser:{email:'owner@example.com'}};
    router={url:'/orders/7',navigate:jasmine.createSpy()};
    component=new OrderDetailComponent({paramMap:params} as any,router,service,auth,{} as any,{} as any); component.ngOnInit();
  });
  afterEach(()=>component.ngOnDestroy());
  it('loads one order and uses saved line totals',()=>{expect(service.getOrder).toHaveBeenCalledWith(7);expect(component.subtotal).toBe(100000);expect(component.shippingFee).toBe(20000);});
  it('retry does not add route subscriptions',()=>{component.reload();service.getOrder.calls.reset();params.next(convertToParamMap({id:'8'}));expect(service.getOrder.calls.count()).toBe(1);});
  it('ignores delayed response for previous order',()=>{const slow=new Subject<any>();service.getOrder.and.returnValue(slow);component.reload();service.getOrder.and.returnValue(of({...order,id:8}));params.next(convertToParamMap({id:'8'}));slow.next(order);expect(component.order?.id).toBe(8);});
  it('clears private data on account change',()=>{auth.currentUser.email='other@example.com';component.ngDoCheck();expect(component.order).toBeNull();expect(router.navigate).toHaveBeenCalledWith(['/login'],jasmine.anything());});
  it('handles forbidden, unavailable and invalid IDs distinctly',()=>{service.getOrder.and.returnValue(throwError({status:404}));component.reload();expect(component.notFound).toBeTrue();service.getOrder.and.returnValue(throwError({status:503}));component.reload();expect(component.loadError).toBeTrue();params.next(convertToParamMap({id:'-1'}));expect(component.notFound).toBeTrue();expect(component.order).toBeNull();});
});

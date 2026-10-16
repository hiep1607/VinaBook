import { of, Subject, throwError } from 'rxjs';
import { ChatOrdersComponent } from './chat-orders.component';
import { chatOrderIds } from 'src/app/shared/order-presentation';
import { ChatMessage } from 'src/app/services/chatbot.service';

describe('Visual order cards', () => {
  let component: ChatOrdersComponent, service: any, auth: any;
  const order: any = {id:7,statusCode:0,status:'Chờ xử lý',total:120000,items:[{name:'Sách A',image:'/a.jpg',price:50000,quantity:2}]};
  beforeEach(() => {
    auth={currentUser:{email:'owner@test.invalid'}};
    service={getOrder:jasmine.createSpy().and.returnValue(of(order)),canCancel:(value:any)=>value.statusCode===0};
    component=new ChatOrdersComponent(service,auth);component.orderIds=[7];
  });
  afterEach(()=>component.ngOnDestroy());
  it('loads authenticated saved order lines without submitting any action',()=>{
    const action=spyOn(component.action,'emit');component.ngOnChanges();
    expect(service.getOrder).toHaveBeenCalledWith(7);expect(component.orders[0].items[0].image).toBe('/a.jpg');expect(action).not.toHaveBeenCalled();
  });
  it('emits a review action for the exact displayed eligible order and respects disabled state',()=>{
    component.ngOnChanges();const action=spyOn(component.action,'emit');component.run('CANCEL_ORDER',component.orders[0]);
    expect(action).toHaveBeenCalledWith(jasmine.objectContaining({type:'CANCEL_ORDER',orderId:7,requiresConfirmation:true}));
    component.disabled=true;component.run('CANCEL_ORDER',component.orders[0]);expect(action).toHaveBeenCalledTimes(1);
  });
  it('clears cards and discards late data from the previous account',()=>{
    const response=new Subject<any>();service.getOrder.and.returnValue(response);component.ngOnChanges();
    auth.currentUser.email='other@test.invalid';component.ngDoCheck();response.next(order);
    expect(component.orders).toEqual([]);expect(component.loading).toBeFalse();
  });
  it('does not request order data for a guest',()=>{
    auth.currentUser=null;component.ngOnChanges();expect(service.getOrder).not.toHaveBeenCalled();
  });
  it('keeps the text fallback when only some requested cards can be fetched',()=>{
    service.getOrder.and.callFake((id:number)=>id===7?of(order):throwError({status:404}));component.orderIds=[7,8];
    const loaded=spyOn(component.loaded,'emit');component.ngOnChanges();
    expect(component.orders.length).toBe(1);expect(component.error).not.toBe('');expect(loaded).not.toHaveBeenCalledWith(true);
  });
  it('reloads status after cancellation without reloading on an equivalent input array',()=>{
    component.ngOnChanges();component.orderIds=[7];component.ngOnChanges();expect(service.getOrder).toHaveBeenCalledTimes(1);
    service.getOrder.and.returnValue(of({...order,statusCode:3}));component.refreshKey++;component.ngOnChanges();expect(component.orders[0].statusCode).toBe(3);
  });
  it('extracts only explicit server lookup IDs, not arbitrary order mentions',()=>{
    const message:ChatMessage={from:'bot',intent:'ORDER_SUPPORT',execution:{tool:'getMyOrders',status:'COMPLETED'},text:'Mình tìm thấy:\n• #7 — Chờ xử lý\n• #8 — Đã hủy'};
    expect(chatOrderIds(message)).toEqual([7,8]);
    expect(chatOrderIds({...message,from:'user'})).toEqual([]);
    expect(chatOrderIds({...message,streaming:true})).toEqual([]);
    expect(chatOrderIds({...message,execution:{tool:'prepareCancelOrder',status:'AWAITING_INPUT'}})).toEqual([]);
    expect(chatOrderIds({...message,text:'Đơn #7: Chờ xử lý'})).toEqual([7]);
  });
});

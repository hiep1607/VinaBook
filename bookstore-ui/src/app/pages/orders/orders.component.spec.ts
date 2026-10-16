import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject } from 'rxjs';
import { OrdersComponent } from './orders.component';
import { OrderService } from 'src/app/services/order.service';
import { AuthService } from 'src/app/services/auth.service';
import { PaymentService } from 'src/app/services/payment.service';
import { NotificationService } from 'src/app/services/notification.service';
import { FabCollisionService } from 'src/app/services/fab-collision.service';

describe('Order list filters and account data', () => {
  let fixture: ComponentFixture<OrdersComponent>, component: OrdersComponent, service: any, auth: any;
  const orders = [
    {id:7,date:'2026-10-13',statusCode:0,status:'Chờ xử lý',paymentMethod:'COD',paymentStatus:'UNPAID',total:120000,items:[{name:'Đắc Nhân Tâm',quantity:2,price:50000}]},
    {id:8,date:'2026-10-13',statusCode:3,status:'Đã hủy',paymentMethod:'COD',paymentStatus:'UNPAID',total:90000,items:[{name:'Harry Potter',quantity:1,price:70000}]}
  ];
  beforeEach(async () => {
    auth = {isLoggedIn:()=>true,currentUser:{email:'owner@test.invalid'}};
    service = {getOrderPage:jasmine.createSpy().and.returnValue(of({items:orders.map(order=>({...order})).reverse(),total:2,counts:{'-1':2,'0':1,'3':1}})),canCancel:(order:any)=>order.statusCode===0,statusLabel:()=> 'Đã hủy'};
    await TestBed.configureTestingModule({imports:[CommonModule,FormsModule,RouterTestingModule],declarations:[OrdersComponent],schemas:[NO_ERRORS_SCHEMA],providers:[
      {provide:FabCollisionService,useValue:{recheck:()=>{}}},
      {provide:OrderService,useValue:service},{provide:AuthService,useValue:auth},{provide:PaymentService,useValue:{}},{provide:NotificationService,useValue:{}}
    ]}).compileComponents();
    spyOn(TestBed.inject(Router),'navigate').and.returnValue(Promise.resolve(true));
    fixture=TestBed.createComponent(OrdersComponent);component=fixture.componentInstance;fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  it('filters by status together with accent-insensitive name or order number',()=>{
    component.activeStatus=0;component.query='dac nhan';expect(component.filteredOrders.map(order=>order.id)).toEqual([7]);
    component.query='#8';expect(component.filteredOrders.length).toBe(0);
    component.activeStatus=-1;expect(component.filteredOrders[0].id).toBe(8);
    component.clearFilters();expect(component.filteredOrders.map(order=>order.id)).toEqual([8,7]);
  });
  it('updates the counts and filtered list after a cancellation result',()=>{
    component.activeStatus=0;component.orderCancelled(7);
    expect(service.getOrderPage).toHaveBeenCalledTimes(2);
  });
  it('opens cancellation review without submitting a cancellation or altering the order',()=>{
    component.requestCancellation(component.orders.find(order=>order.id===7));
    expect(component.cancellingOrderId).toBe(7);expect(component.orders.find(order=>order.id===7).statusCode).toBe(0);
  });
  it('clears private orders and ignores late responses on account changes',()=>{
    const pending=new Subject<any>();service.getOrderPage.and.returnValue(pending);component.ngOnInit();
    const router=TestBed.inject(Router);auth.currentUser.email='other@test.invalid';component.ngDoCheck();pending.next(orders);
    expect(component.orders).toEqual([]);expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});

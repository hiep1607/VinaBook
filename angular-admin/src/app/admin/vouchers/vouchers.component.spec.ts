import { CommonModule } from '@angular/common';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { PageService } from '../../services/page.service';
import { environment } from 'src/environments/environment';
import { VouchersComponent } from './vouchers.component';

describe('Voucher workspace', () => {
 let fixture: ComponentFixture<VouchersComponent>, http: HttpTestingController;
 const api=environment.apiUrl+'/api/vouchers/admin';
 const row={id:7,code:'DOCSACH10',kind:'PERCENT',value:10,maximumDiscount:50000,minimumSubtotal:100000,usageLimit:100,perUserLimit:1,active:true,startsAt:new Date(Date.now()-86400000).toISOString(),expiresAt:new Date(Date.now()+86400000).toISOString()};
 beforeEach(async () => {
  await TestBed.configureTestingModule({declarations:[VouchersComponent],imports:[CommonModule,FormsModule,HttpClientTestingModule],providers:[{provide:PageService,useValue:{setPageActive:()=>{}}}]}).compileComponents();
  fixture=TestBed.createComponent(VouchersComponent);http=TestBed.inject(HttpTestingController);fixture.detectChanges();
  http.expectOne(api).flush([row,{...row,id:8,code:'TAT20',active:false}]);fixture.detectChanges();
 });
 afterEach(()=>{http.verify();fixture.destroy();});
 it('shows saved codes and distinguishes disabled offers',()=>{
  expect(fixture.nativeElement.textContent).toContain('DOCSACH10');
  expect(fixture.nativeElement.textContent).toContain('Đang áp dụng');
  expect(fixture.nativeElement.textContent).toContain('Đã tắt');
 });
 it('filters loaded offers without changing server data',()=>{
  const component=fixture.componentInstance;component.query='docsach';component.stateFilter='active';fixture.detectChanges();
  expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  component.stateFilter='disabled';fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Không tìm thấy mã phù hợp');
  http.expectNone(request=>request.method!=='GET');
 });
 it('edits a copy and submits the same voucher ID only once',()=>{
  const component=fixture.componentInstance;component.edit(component.rows[0]);component.draft.value=15;fixture.detectChanges();
  expect((fixture.nativeElement.querySelector('#voucher-code') as HTMLInputElement).readOnly).toBeTrue();
  expect(component.rows[0].value).toBe(10);
  component.save();component.save();
  const save=http.expectOne(request=>request.url===api&&request.method==='PUT');
  expect(save.request.body.id).toBe(7);expect(save.request.body.value).toBe(15);
  save.flush({...row,value:15});http.expectOne(request=>request.url===api&&request.method==='GET').flush([{...row,value:15}]);
  expect(component.success).toContain('Đã lưu');
 });
 it('keeps the draft and server explanation after a failed save',()=>{
  const component=fixture.componentInstance;component.edit(component.rows[0]);component.draft.value=20;component.save();
  http.expectOne(request=>request.method==='PUT').flush({message:'Mã đã thay đổi. Bạn kiểm tra lại.'},{status:409,statusText:'Conflict'});
  fixture.detectChanges();expect(component.draft.value).toBe(20);expect(component.saving).toBeFalse();
  expect(fixture.nativeElement.textContent).toContain('Mã đã thay đổi');
 });
 it('blocks invalid dates and resets list errors on retry',()=>{
  const component=fixture.componentInstance;component.start='invalid';component.end='invalid';component.save();
  http.expectNone(request=>request.method==='PUT');expect(component.saveError).toContain('thời gian');
  component.load();http.expectOne(api).flush({}, {status:503,statusText:'Unavailable'});
  expect(component.error).not.toBe('');component.load();http.expectOne(api).flush([row]);
  expect(component.error).toBe('');expect(component.busy).toBeFalse();
 });
});

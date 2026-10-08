import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { environment } from 'src/environments/environment';
import { PageService } from '../../services/page.service';
import { SupportRequestsComponent } from './support-requests.component';
@Component({selector:'app-support-chat',template:''})
class ChatStub { @Input() requestId=0; @Input() closed=false; }
@Component({selector:'app-payment-refunds',template:''})
class RefundStub { @Input() orderId=0; }

describe('Admin support queue', () => {
  let fixture: ComponentFixture<SupportRequestsComponent>, http: HttpTestingController;
  const api = environment.apiUrl + '/api/support/admin';
  beforeEach(async () => {
    await TestBed.configureTestingModule({ declarations: [SupportRequestsComponent, ChatStub, RefundStub], imports: [CommonModule, FormsModule, HttpClientTestingModule],
      providers: [{ provide: PageService, useValue: { setPageActive: () => {} } }] }).compileComponents();
    fixture = TestBed.createComponent(SupportRequestsComponent); http = TestBed.inject(HttpTestingController); fixture.detectChanges();
    http.expectOne(request => request.url === api && request.params.get('status') === 'OPEN').flush({ items: [{ id: 9, status: 'OPEN', version: 2, kind: 'RETURN', message: 'Sách thiếu trang', reply: '' }], page: 0, hasNext: false });
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });
  it('preserves replies when selecting another request without saving', () => {
    const component=fixture.componentInstance;
    component.requests.push({...component.requests[0],id:10,message:'Giao hàng trễ',draftReply:''});
    component.requests[0].draftReply='Nội dung đang soạn';
    component.select(component.requests[1]);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Giao hàng trễ');
    component.select(component.requests[0]);
    expect(component.selectedRequest?.draftReply).toBe('Nội dung đang soạn');
    http.expectNone(request=>request.method==='PUT');
  });
  it('uses the server status filter and resets selection to the returned page', () => {
    const component=fixture.componentInstance;component.chatId=9;component.refundOpen=true;component.status='RESOLVED';component.changeStatus();
    http.expectOne(request=>request.url===api&&request.params.get('status')==='RESOLVED'&&request.params.get('page')==='0').flush({items:[],page:0,hasNext:false});
    fixture.detectChanges();expect(component.selectedRequest).toBeUndefined();expect(component.chatId).toBe(0);expect(component.refundOpen).toBeFalse();
  });
  it('shows a real request without saving until staff enter a reply', () => {
    expect(fixture.nativeElement.textContent).toContain('Sách thiếu trang');
    fixture.componentInstance.save(fixture.componentInstance.requests[0]); http.expectNone(request => request.method === 'PUT');
  });
  it('sends the current version and blocks a repeated save while waiting', () => {
    const component = fixture.componentInstance, request = component.requests[0]; request.draftReply = 'Cửa hàng đang kiểm tra.';
    component.save(request); component.save(request);
    const save = http.expectOne(api + '/9'); expect(save.request.body.version).toBe(2); expect(save.request.body.status).toBe('IN_REVIEW');
    save.flush({ ...request, status: 'IN_REVIEW', reply: request.draftReply, version: 3 }); expect(request.version).toBe(3);
  });
  it('shows a version conflict instead of silently overwriting another reply', () => {
    const component = fixture.componentInstance, request = component.requests[0]; request.draftReply = 'Phản hồi mới'; component.save(request);
    http.expectOne(api + '/9').flush({ message: 'Yêu cầu đã thay đổi. Tải lại trước khi phản hồi.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges(); expect(component.error).toContain('Tải lại'); expect(request.version).toBe(2);
  });
});

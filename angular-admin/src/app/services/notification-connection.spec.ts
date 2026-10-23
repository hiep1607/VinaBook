import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { WebSocketService } from './web-socket.service';
import { SessionService } from './session.service';
import { environment } from 'src/environments/environment';

describe('Shared notification connection', () => {
  it('uses one authenticated ticket connection and closes after the last consumer', () => {
    TestBed.configureTestingModule({imports:[HttpClientTestingModule],providers:[{provide:SessionService,useValue:{getToken:()=> 'synthetic-admin-token'}}]});
    const service=TestBed.inject(WebSocketService),http=TestBed.inject(HttpTestingController);
    const socket:any={close:jasmine.createSpy('close')};
    const factory=spyOn<any>(service,'createSocket').and.returnValue(socket);
    let first=0,second=0;
    const a=service.watch().subscribe(()=>first++),b=service.watch().subscribe(()=>second++);
    const request=http.expectOne(environment.apiUrl+'/api/notification/socket-ticket');
    expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-admin-token');
    request.flush({ticket:'single-use-ticket'});
    expect(factory).toHaveBeenCalledTimes(1);
    expect(factory.calls.mostRecent().args[0]).not.toContain('synthetic-admin-token');
    socket.onmessage({data:'{"type":"ORDER_CHANGED","orderId":7,"message":"Đơn được cập nhật"}'});
    socket.onmessage({data:'malformed'});
    expect(first).toBe(1);expect(second).toBe(1);
    a.unsubscribe();expect(socket.close).not.toHaveBeenCalled();
    b.unsubscribe();expect(socket.close).toHaveBeenCalledTimes(1);http.verify();
  });
});

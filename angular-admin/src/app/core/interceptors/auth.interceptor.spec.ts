import {
  HttpHandler,
  HttpRequest,
  HttpResponse
} from '@angular/common/http';
import { of } from 'rxjs';

import { SessionService } from '../../services/session.service';
import { AuthInterceptor } from './auth.interceptor';

describe('AuthInterceptor', () => {
  let session: jasmine.SpyObj<SessionService>;
  let next: jasmine.SpyObj<HttpHandler>;
  let interceptor: AuthInterceptor;

  beforeEach(() => {
    session = jasmine.createSpyObj<SessionService>(
      'SessionService',
      ['getToken']
    );
    next = jasmine.createSpyObj<HttpHandler>('HttpHandler', ['handle']);
    next.handle.and.returnValue(of(new HttpResponse({ status: 200 })));
    interceptor = new AuthInterceptor(session);
  });

  function forwardedRequest(url: string): HttpRequest<unknown> {
    const request = new HttpRequest('GET', url);
    interceptor.intercept(request, next).subscribe();
    return next.handle.calls.mostRecent().args[0] as HttpRequest<unknown>;
  }

  it('adds bearer token to backend API requests', () => {
    session.getToken.and.returnValue('test-token');

    const request = forwardedRequest('/api/orders');

    expect(request.headers.get('Authorization'))
      .toBe('Bearer test-token');
  });

  it('does not add authorization when token is absent', () => {
    session.getToken.and.returnValue(null);

    const request = forwardedRequest('/api/orders');

    expect(request.headers.has('Authorization')).toBeFalse();
  });

  it('does not add authorization to external requests', () => {
    session.getToken.and.returnValue('test-token');

    const request = forwardedRequest('https://example.com/resource');

    expect(request.headers.has('Authorization')).toBeFalse();
  });
});

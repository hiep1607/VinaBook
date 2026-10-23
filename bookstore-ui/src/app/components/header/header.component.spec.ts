import { AutomationService } from 'src/app/services/automation.service';
import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, DefaultUrlSerializer, NavigationEnd, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { CategoryService } from '../../services/category.service';
import { SearchHistoryService } from '../../services/search-history.service';
import { SearchService } from '../../services/search.service';
import { WishlistService } from '../../services/wishlist.service';
import { HeaderComponent } from './header.component';

describe('Header search and credential autofill', () => {
  let fixture: ComponentFixture<HeaderComponent>, component: HeaderComponent;
  let events: Subject<NavigationEnd>, router: any, history: any;
  beforeEach(async () => {
    events = new Subject();
    router = {url: '/login', events, navigate: jasmine.createSpy(),
      parseUrl: (url: string) => new DefaultUrlSerializer().parse(url), serializeUrl: (url: any) => String(url),
      createUrlTree: () => new DefaultUrlSerializer().parse('/')};
    history = {getHistory: () => [], addToHistory: jasmine.createSpy()};
    await TestBed.configureTestingModule({declarations:[HeaderComponent],providers:[
      {provide:AutomationService,useValue:{unreadCount:0,connect:()=>()=>{},syncSession:()=>{}}},
      {provide:Router,useValue:router}, {provide:ActivatedRoute,useValue:{queryParams:of({})}},
      {provide:AuthService,useValue:{isLoggedIn:()=>false}},
      {provide:CartService,useValue:{getCartCount:()=>0,getCartItems:()=>[]}},
      {provide:WishlistService,useValue:{getCount:()=>0}},
      {provide:SearchService,useValue:{getSuggestions:()=>[]}},
      {provide:SearchHistoryService,useValue:history},
      {provide:CategoryService,useValue:{getAll:()=>of([])}}
    ]}).compileComponents();
    fixture=TestBed.createComponent(HeaderComponent); component=fixture.componentInstance; fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  it('announces unread notices and bounds the visible bell badge', () => {
    const auth = TestBed.inject(AuthService) as any;
    auth.isLoggedIn = () => true; auth.currentUser = {name:'Bạn đọc',email:'reader@example.test'};
    const notices = TestBed.inject(AutomationService) as any; notices.unreadCount = 120; fixture.detectChanges();
    const bell = fixture.nativeElement.querySelector('a.notification-icon');
    expect(bell.getAttribute('aria-label')).toBe('Thông báo, 120 chưa đọc');
    expect(bell.querySelector('.wishlist-badge').textContent.trim()).toBe('99+');
    notices.unreadCount = 0; fixture.detectChanges();
    expect(bell.getAttribute('aria-label')).toBe('Thông báo'); expect(bell.querySelector('.wishlist-badge')).toBeNull();
  });
  it('marks book search as a separate search form rather than a credential field', () => {
    const input:HTMLInputElement=fixture.nativeElement.querySelector('#site-search');
    expect(input.type).toBe('search'); expect(input.name).toBe('bookQuery');
    expect(input.form?.getAttribute('role')).toBe('search'); expect(input.form?.getAttribute('autocomplete')).toBe('off');
    expect(input.placeholder).toBe('Tìm kiếm sách, tác giả, thể loại...');
  });
  it('clears an autofilled email after leaving login even without a model event', () => {
    const input:HTMLInputElement=fixture.nativeElement.querySelector('#site-search'); input.value='reader@example.test';
    expect(component.keyword).toBe(''); events.next(new NavigationEnd(1,'/','/'));
    expect(input.value).toBe(''); expect(component.keyword).toBe(''); expect(history.addToHistory).not.toHaveBeenCalled();
  });
  it('cancels delayed suggestions containing a stale autofilled value after login', fakeAsync(() => {
    component.keyword='reader@example.test'; component.onInput();
    events.next(new NavigationEnd(1,'/','/')); tick(350);
    expect(component.keyword).toBe(''); expect(component.showSuggestions).toBeFalse();
    expect(history.addToHistory).not.toHaveBeenCalled();
  }));
  it('keeps genuine search text when navigating between ordinary pages', () => {
    events.next(new NavigationEnd(1,'/','/')); component.keyword='Dế Mèn phiêu lưu ký';
    events.next(new NavigationEnd(2,'/category/1','/category/1'));
    expect(component.keyword).toBe('Dế Mèn phiêu lưu ký');
  });
  it('restores the actual search query when returning from login to search', () => {
    component.keyword='reader@example.test'; events.next(new NavigationEnd(1,'/search?q=D%E1%BA%BF%20M%C3%A8n','/search?q=D%E1%BA%BF%20M%C3%A8n'));
    expect(component.keyword).toBe('Dế Mèn');
  });
  it('submits the search form only once and records the actual book query', fakeAsync(() => {
    component.keyword='  Dế Mèn  '; fixture.detectChanges(); flushMicrotasks();
    fixture.nativeElement.querySelector('form[role="search"]').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
    expect(router.navigate).toHaveBeenCalledOnceWith(['/search'],{queryParams:{q:'Dế Mèn'}});
    expect(history.addToHistory).toHaveBeenCalledOnceWith('Dế Mèn');
  }));
});

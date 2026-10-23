import { ElementRef } from '@angular/core';
import { CountUpDirective } from './count-up.directive';

describe('CountUpDirective cập nhật dữ liệu tải muộn', () => {
  it('cập nhật đích mới sau khi đã hiển thị, kể cả chế độ giảm chuyển động', () => {
    spyOn(window, 'matchMedia').and.returnValue({matches: true} as MediaQueryList);
    const host = document.createElement('span');
    const directive = new CountUpDirective(new ElementRef(host));
    (directive as any).started = true;
    directive.target = 0;directive.ngOnChanges();expect(host.textContent).toBe('0');
    directive.target = 156;directive.suffix = '+';directive.ngOnChanges();
    expect(host.textContent).toBe('156+');directive.ngOnDestroy();
  });
});

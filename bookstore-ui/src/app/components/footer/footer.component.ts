import { Component } from '@angular/core';

import { environment } from 'src/environments/environment';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent {
  readonly year = new Date().getFullYear();

  /**
   * Link mạng xã hội lấy từ cấu hình environment (socialLinks).
   * Chỉ render khi có URL thật được cấu hình; không tự bịa URL placeholder.
   */
  readonly socialLinks = environment.socialLinks ?? [];
}

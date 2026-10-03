import { Component } from '@angular/core';

import { NotificationService } from 'src/app/services/notification.service';
import { StoreKnowledgeService } from 'src/app/services/store-knowledge.service';

@Component({
  selector: 'app-contact',
  templateUrl: './contact.component.html',
  styleUrls: ['./contact.component.scss']
})
export class ContactComponent {

  form = { name: '', email: '', subject: '', message: '' };
  submitted = false;

  faqs = [
    {
      q: 'Bao lâu thì tôi nhận được sách?',
      a: 'Đang tải thông tin giao hàng.',
      open: false
    },
    {
      q: 'VinaBook có hỗ trợ đổi trả không?',
      a: 'Đang tải chính sách đổi trả.',
      open: false
    },
    {
      q: 'Tôi có thể thanh toán bằng hình thức nào?',
      a: 'Đang tải phương thức thanh toán.',
      open: false
    },
    {
      q: 'Làm sao để theo dõi đơn hàng?',
      a: 'Đăng nhập và mở mục Đơn hàng của tôi. Trạng thái chờ xử lý, đang giao, hoàn thành hoặc đã hủy được cập nhật từ hệ thống quản trị.',
      open: false
    },
    {
      q: 'Phí vận chuyển được tính thế nào?',
      a: 'Đang tải phí vận chuyển.',
      open: false
    }
  ];

  constructor(private notification: NotificationService, private knowledge: StoreKnowledgeService) {
    this.knowledge.get().subscribe(data => {
      const shipping = StoreKnowledgeService.shippingText(data.settings);
      this.faqs[0].a = `Thời gian giao dự kiến ${data.settings.deliveryMinDays}–${data.settings.deliveryMaxDays} ngày làm việc. Bạn có thể theo dõi trong mục Đơn hàng của tôi.`;
      this.faqs[4].a = shipping;
      this.faqs[1].a = data.policies.find(p => p.policyKey === 'returns')?.body || 'Vui lòng liên hệ cửa hàng để biết chính sách đổi trả hiện hành.';
      const available = Object.values(data.paymentMethods).filter(method => method.available).map(method => method.label);
      this.faqs[2].a = available.length ? `Các phương thức đang khả dụng: ${available.join(', ')}.` : 'Vui lòng kiểm tra phương thức thanh toán ở bước đặt hàng.';
    }, () => this.faqs.forEach((faq, index) => {
      if (index !== 3) { faq.a = 'Thông tin này tạm thời chưa tải được. Vui lòng thử lại sau.'; }
    }));
  }

  isValid(): boolean {
    return this.form.name.trim().length > 0
      && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email.trim())
      && this.form.message.trim().length > 0;
  }

  submit(): void {
    this.submitted = true;

    if (!this.isValid()) {
      this.notification.error('Vui lòng điền đầy đủ họ tên, email hợp lệ và nội dung');
      return;
    }

    const subject = encodeURIComponent(this.form.subject.trim() || 'Yêu cầu hỗ trợ từ website VinaBook');
    const body = encodeURIComponent(
      `Họ tên: ${this.form.name.trim()}\nEmail: ${this.form.email.trim()}\n\n${this.form.message.trim()}`
    );

    window.location.href = `mailto:hotro@vinabook.vn?subject=${subject}&body=${body}`;
    this.notification.info('Ứng dụng email đã được mở. Vui lòng kiểm tra và bấm Gửi.');
  }

  toggleFaq(faq: any): void {
    faq.open = !faq.open;
  }
}

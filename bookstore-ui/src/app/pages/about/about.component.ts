import { Component, OnInit } from '@angular/core';
import { CategoryService } from 'src/app/services/category.service';
import { ProductService } from 'src/app/services/product.service';
import { StoreKnowledgeService, StoreSettings } from 'src/app/services/store-knowledge.service';

@Component({
  selector: 'app-about',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss']
})
export class AboutComponent implements OnInit {

  bookCount = 0;
  categoryCount = 0;
  shippingSettings?: StoreSettings;

  reasons = [
    {
      icon: '📚',
      title: 'Kho sách phong phú',
      text: 'Đầy đủ thể loại từ văn học, kinh tế đến thiếu nhi, được tuyển chọn kỹ lưỡng từ các nhà xuất bản uy tín.'
    },
    {
      icon: '🚚',
      title: 'Giao hàng toàn quốc',
      text: 'Đang tải thông tin giao hàng.'
    },
    {
      icon: '💰',
      title: 'Giá tốt mỗi ngày',
      text: 'Khuyến mãi liên tục và giá luôn cạnh tranh để bạn yên tâm chọn sách hay.'
    },
    {
      icon: '🤝',
      title: 'Tận tâm với bạn đọc',
      text: 'Hỗ trợ tư vấn chọn sách và giải đáp chính sách cửa hàng.'
    }
  ];

  constructor(
    private productService: ProductService,
    private categoryService: CategoryService,
    private knowledge: StoreKnowledgeService
  ) {}

  ngOnInit(): void {
    this.productService.getProducts().subscribe(
      products => this.bookCount = products.length,
      () => this.bookCount = 0
    );
    this.categoryService.getAll().subscribe(
      categories => this.categoryCount = categories.length,
      () => this.categoryCount = 0
    );
    this.knowledge.get().subscribe(data => {
      this.shippingSettings = data.settings;
      this.reasons[1].text = StoreKnowledgeService.shippingText(data.settings);
      const returns = data.policies.find(p => p.policyKey === 'returns');
      if (returns) { this.reasons[3].text = returns.body; }
    }, () => this.reasons[1].text = 'Thông tin giao hàng tạm thời chưa tải được.');
  }

}

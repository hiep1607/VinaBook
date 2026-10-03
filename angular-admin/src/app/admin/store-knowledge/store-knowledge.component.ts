import { Component, OnInit } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { PageService } from '../../services/page.service';
import { StoreKnowledgeService, StorePolicy, StoreSettings } from '../../services/store-knowledge.service';

@Component({
  selector: 'app-store-knowledge',
  templateUrl: './store-knowledge.component.html',
  styleUrls: ['./store-knowledge.component.css']
})
export class StoreKnowledgeComponent implements OnInit {
  settings?: StoreSettings;
  policies: StorePolicy[] = [];
  history: StorePolicy[] = [];
  loading = true;
  saving = false;

  constructor(private knowledge: StoreKnowledgeService, private pages: PageService, private toastr: ToastrService) { }
  ngOnInit(): void {
    this.pages.setPageActive('store-knowledge');
    this.reload();
  }
  reload(): void {
    this.knowledge.get().subscribe(data => {
      this.settings = { ...data.settings };
      const now = new Date();
      const effectiveAt = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      this.policies = ['returns', 'cancellation', 'support'].map(key =>
        ({ ...(data.policies.find(p => p.policyKey === key) || { policyKey: key, title: key, body: '' }), effectiveAt }));
      this.history = data.history || [];
      this.loading = false;
    }, () => { this.loading = false; this.toastr.error('Không tải được chính sách.', 'Hệ thống'); });
  }
  saveSettings(): void {
    if (!this.settings || this.saving) { return; }
    this.saving = true;
    this.knowledge.saveSettings(this.settings).subscribe(saved => {
      this.settings = saved; this.saving = false;
      this.toastr.success('Đã cập nhật phí và thời gian giao hàng.', 'Hệ thống');
    }, error => {
      this.saving = false;
      this.toastr.error(error?.error?.message || 'Cấu hình không hợp lệ.', 'Hệ thống');
    });
  }
  publish(policy: StorePolicy): void {
    if (this.saving) { return; }
    this.saving = true;
    this.knowledge.publishPolicy(policy).subscribe(saved => {
      Object.assign(policy, saved); this.saving = false;
      this.toastr.success('Đã xuất bản chính sách.', 'Hệ thống');
      this.reload();
    }, error => {
      this.saving = false;
      this.toastr.error(error?.error?.message || 'Không xuất bản được chính sách.', 'Hệ thống');
    });
  }
}

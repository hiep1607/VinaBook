import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
import { BookProfileInput, BookRelationInput } from '../../common/BookKnowledge';
import { ProductService } from '../../services/product.service';

@Component({
  selector: 'app-book-knowledge',
  templateUrl: './book-knowledge.component.html',
  styleUrls: ['./book-knowledge.component.css']
})
export class BookKnowledgeComponent {
  @Input() id!: number;
  @Output() published = new EventEmitter<void>();
  profile?: BookProfileInput;
  topicsText = '';
  relationsText = '';
  busy = false;

  constructor(private modal: NgbModal, private products: ProductService, private toastr: ToastrService) { }

  open(content: TemplateRef<any>): void {
    this.busy = true;
    this.products.getKnowledge(this.id).subscribe(profile => {
      this.apply(profile);
      this.busy = false;
      this.modal.open(content, { centered: true, size: 'lg', scrollable: true });
    }, () => {
      this.busy = false;
      this.toastr.error('Không tải được hồ sơ sách.', 'Hệ thống');
    });
  }

  suggest(): void {
    if (this.busy) { return; }
    this.busy = true;
    this.products.suggestKnowledge(this.id).subscribe(profile => {
      this.apply(profile);
      this.busy = false;
      this.toastr.info('Hãy kiểm tra từng thông tin trước khi xuất bản.', 'Bản gợi ý AI');
    }, () => {
      this.busy = false;
      this.toastr.error('Không tạo được gợi ý. Bạn vẫn có thể nhập hồ sơ thủ công.', 'Hệ thống');
    });
  }

  publish(): void {
    if (!this.profile || this.busy) { return; }
    const topics = this.topicsText.split(/[,\n]/).map(s => s.trim()).filter(Boolean);
    if (!this.profile.summary?.trim() || !topics.length) {
      this.toastr.warning('Cần tóm tắt và ít nhất một chủ đề.', 'Hồ sơ sách'); return;
    }
    const relations = this.parseRelations();
    if (relations === null) { return; }
    this.busy = true;
    this.products.publishKnowledge(this.id, { ...this.profile, topics, relations }).subscribe(profile => {
      this.apply(profile);
      this.busy = false;
      this.published.emit();
      this.toastr.success('Hồ sơ đã được duyệt; sách có thể tìm thấy ngay.', 'Hệ thống');
      this.modal.dismissAll();
    }, error => {
      this.busy = false;
      this.toastr.error(error?.error?.message || 'Không xuất bản được. Bản nháp vẫn còn nguyên.', 'Hệ thống');
    });
  }

  private apply(profile: BookProfileInput): void {
    this.profile = profile;
    this.topicsText = (profile.topics || []).join(', ');
    this.relationsText = (profile.relations || [])
      .map(r => `${r.type} | ${r.targetProductId} | ${r.evidence || ''}`).join('\n');
  }

  private parseRelations(): BookRelationInput[] | null {
    const allowed = ['SAME_SERIES', 'NEXT_VOLUME', 'READ_BEFORE'];
    const result: BookRelationInput[] = [];
    for (const line of this.relationsText.split('\n').map(s => s.trim()).filter(Boolean)) {
      const [typeRaw, idRaw, ...evidenceParts] = line.split('|').map(s => s.trim());
      const targetProductId = Number(idRaw);
      if (!allowed.includes(typeRaw) || !Number.isInteger(targetProductId) || targetProductId <= 0) {
        this.toastr.warning('Quan hệ phải theo dạng LOẠI | MÃ SÁCH | CĂN CỨ.', 'Hồ sơ sách');
        return null;
      }
      result.push({ type: typeRaw as BookRelationInput['type'], targetProductId, evidence: evidenceParts.join(' | ') });
    }
    return result;
  }
}

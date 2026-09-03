import { Component, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
import { Category } from '../../common/Category';
import { Product } from '../../common/Product';
import { CategoryService } from '../../services/category.service';
import { ProductService } from '../../services/product.service';
import { UploadService } from '../../services/upload.service';
import { ProductPreviewPage } from '../../common/ProductPreview';

@Component({
  selector: 'app-edit-product',
  templateUrl: './edit-product.component.html',
  styleUrls: ['./edit-product.component.css']
})
export class EditProductComponent implements OnInit {

  product!: Product;

  selectFile!: File;
  image: string = '';
  previewPages: ProductPreviewPage[] = [];
  previewEnabled = false;
  rightsConfirmed = false;
  previewUploading = false;
  replacingPage?: ProductPreviewPage;

  postForm: FormGroup;
  categories!: Category[];

  @Input() id!: number;
  @Output()
  editFinish: EventEmitter<any> = new EventEmitter<any>();

  constructor(private modalService: NgbModal, private categoryService: CategoryService, private productService: ProductService, private toastr: ToastrService, private uploadService: UploadService) {
    this.postForm = new FormGroup({
      'productId': new FormControl(0),
      'name': new FormControl(null, [Validators.minLength(4), Validators.required]),
      'author': new FormControl(null, Validators.required),
      'quantity': new FormControl(null, [Validators.min(1), Validators.required]),
      'price': new FormControl(null, [Validators.required, Validators.min(1000)]),
      'discount': new FormControl(null, [Validators.required, Validators.min(0), Validators.max(100)]),
      'description': new FormControl(null, Validators.required),
      'enteredDate': new FormControl(new Date()),
      'categoryId': new FormControl(1),
      'status': new FormControl(1),
      'sold': new FormControl(0),
    })
  }

  ngOnInit(): void {
    this.getCategories();
    this.getProduct();
    this.getPreview();
  }

  update() {
    if (!this.image) {
      this.toastr.warning('Hãy chọn ảnh bìa cho sách!', 'Hệ thống');
      return;
    }
    if (this.postForm.valid) {
      this.product = this.postForm.value;
      this.product.category = new Category(this.postForm.value.categoryId, '');
      this.product.image = this.image;

      this.productService.update(this.product, this.id).subscribe(data => {
        this.productService.updatePreviewSettings(this.id, this.previewEnabled, this.rightsConfirmed).subscribe(() => {
          this.toastr.success('Cập nhật thành công!', 'Hệ thống');
          this.editFinish.emit('done');
          this.modalService.dismissAll();
        }, error => {
          this.toastr.error(this.previewEnabled ? 'Không thể bật đọc thử: cần đủ 5–10 trang và xác nhận quyền.' : 'Lưu bản đọc thử thất bại!', 'Hệ thống');
        });
      }, error => {
        this.toastr.error('Cập nhật thất bại!', 'Hệ thống');
      })
    } else {
      this.toastr.error('Hãy kiểm tra lại dữ liệu!', 'Hệ thống');
    }
    if (!this.postForm.valid) {
      return;
    }
  }

  getProduct() {
    this.productService.getOne(this.id).subscribe(data => {
      this.product = data as Product;
      this.postForm = new FormGroup({
        'productId': new FormControl(this.product.productId),
        'name': new FormControl(this.product.name, [Validators.minLength(4), Validators.required]),
        'author': new FormControl(this.product.author, Validators.required),
        'quantity': new FormControl(this.product.quantity, [Validators.min(1), Validators.required]),
        'price': new FormControl(this.product.price, [Validators.required, Validators.min(1000)]),
        'discount': new FormControl(this.product.discount, [Validators.required, Validators.min(0), Validators.max(100)]),
        'description': new FormControl(this.product.description, Validators.required),
        'enteredDate': new FormControl(this.product.enteredDate),
        'categoryId': new FormControl(this.product.category.categoryId),
        'status': new FormControl(1),
        'sold': new FormControl(this.product.sold),
      })
      this.image = this.product.image;
    }, error => {
      this.toastr.error('Lỗi truy xuất dữ liệu! ', 'Hệ thống');
    })
  }

  getPreview(): void {
    this.productService.getPreview(this.id).subscribe(data => {
      this.previewPages = (data.pages || []).sort((a, b) => a.pageOrder - b.pageOrder);
      this.previewEnabled = !!data.enabled;
      this.rightsConfirmed = !!data.rightsConfirmed;
    }, () => {
      this.previewPages = [];
      this.previewEnabled = false;
      this.rightsConfirmed = false;
    });
  }

  onPreviewFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size > 8 * 1024 * 1024) {
      this.toastr.warning('Chỉ nhận JPG, PNG, WebP tối đa 8MB.', 'Hệ thống');
      return;
    }
    if (this.previewPages.length >= 10) {
      this.toastr.warning('Bản đọc thử tối đa 10 trang.', 'Hệ thống');
      return;
    }
    this.previewUploading = true;
    this.uploadService.uploadPreviewPage(file).subscribe(response => {
      const page: ProductPreviewPage = {
        imageUrl: response.secure_url,
        pageOrder: this.previewPages.length + 1,
        originalPageNumber: this.previewPages.length + 1,
        enabled: true
      };
      this.productService.addPreviewPage(this.id, page).subscribe(saved => {
        this.previewPages = [...this.previewPages, saved].sort((a, b) => a.pageOrder - b.pageOrder);
        this.previewUploading = false;
      }, () => {
        this.previewUploading = false;
        this.toastr.error('Không lưu được trang đọc thử.', 'Hệ thống');
      });
    }, () => {
      this.previewUploading = false;
      this.toastr.error('Tải trang đọc thử thất bại.', 'Hệ thống');
    });
  }

  startReplacePreviewPage(page: ProductPreviewPage): void {
    this.replacingPage = page;
    document.getElementById('replacePreviewPageFile')?.click();
  }

  onPreviewReplaceSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const page = this.replacingPage;
    this.replacingPage = undefined;
    if (!file || !page?.previewPageId) {
      return;
    }
    if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size > 8 * 1024 * 1024) {
      this.toastr.warning('Chỉ nhận JPG, PNG, WebP tối đa 8MB.', 'Hệ thống');
      return;
    }
    this.previewUploading = true;
    this.uploadService.uploadPreviewPage(file).subscribe(response => {
      const replacement: ProductPreviewPage = {
        ...page,
        imageUrl: response.secure_url
      };
      this.productService.updatePreviewPage(this.id, page.previewPageId!, replacement).subscribe(saved => {
        this.previewPages = this.previewPages.map(item => item.previewPageId === saved.previewPageId ? saved : item);
        this.previewUploading = false;
      }, () => {
        this.previewUploading = false;
        this.toastr.error('Không lưu được ảnh thay thế.', 'Hệ thống');
      });
    }, () => {
      this.previewUploading = false;
      this.toastr.error('Tải ảnh thay thế thất bại.', 'Hệ thống');
    });
  }

  removePreviewPage(page: ProductPreviewPage): void {
    if (!page.previewPageId) {
      return;
    }
    this.productService.deletePreviewPage(this.id, page.previewPageId).subscribe(() => {
      this.previewPages = this.previewPages.filter(item => item.previewPageId !== page.previewPageId)
        .map((item, index) => ({ ...item, pageOrder: index + 1 }));
      this.toastr.success('Đã xóa trang đọc thử.', 'Hệ thống');
    }, () => this.toastr.error('Xóa trang đọc thử thất bại.', 'Hệ thống'));
  }

  movePreviewPage(index: number, direction: -1 | 1): void {
    const nextIndex = index + direction;
    if (nextIndex < 0 || nextIndex >= this.previewPages.length) {
      return;
    }
    const pages = [...this.previewPages];
    [pages[index], pages[nextIndex]] = [pages[nextIndex], pages[index]];
    pages.forEach((page, position) => page.pageOrder = position + 1);
    this.previewPages = pages;
    const ids = pages.map(page => page.previewPageId).filter((id): id is number => !!id);
    if (ids.length === pages.length) {
      this.productService.reorderPreviewPages(this.id, ids).subscribe();
    }
  }

  getCategories() {
    this.categoryService.getAll().subscribe(data => {
      this.categories = data as Category[];
    }, error => {
      this.toastr.error('Lỗi truy xuất dữ liệu, bấm f5!', 'Hệ thống');
    })
  }

  onFileSelect(event: any) {
    this.selectFile = event.target.files[0];
    this.uploadService.uploadProduct(this.selectFile).subscribe(response => {
      if (response) {
        this.image = response.secure_url;
      }
    })
  }

  open(content: TemplateRef<any>) {
    this.modalService.open(content, { centered: true, size: 'lg' });
  }

  onImgError() {
    this.image = '';
  }

}

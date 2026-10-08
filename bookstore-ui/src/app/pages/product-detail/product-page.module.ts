import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {LucideAngularModule} from 'lucide-angular';
import {CustomerComponentsModule} from '../../shared/customer-components.module';
import {ProductDetailComponent} from './product-detail.component';
import {Book3dViewerComponent} from '../../components/book-3d-viewer/book-3d-viewer.component';
import {BookPreviewReaderComponent} from '../../components/book-preview-reader/book-preview-reader.component';
@NgModule({imports:[CommonModule,FormsModule,LucideAngularModule,CustomerComponentsModule,RouterModule.forChild([{path:'',component:ProductDetailComponent}])],declarations:[ProductDetailComponent,Book3dViewerComponent,BookPreviewReaderComponent]})
export class ProductPageModule {}

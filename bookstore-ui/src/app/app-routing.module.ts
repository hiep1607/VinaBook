import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { SearchComponent } from './pages/search/search.component';
import { CategoryComponent } from './pages/category/category.component';
import { BooksComponent } from './pages/books/books.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { AuthGuard } from './core/guards/auth.guard';
import { PaymentResultComponent } from './pages/payment-result/payment-result.component';

const routes: Routes = [

  { path: '', component: HomeComponent, data: { pageTitle: 'Nhà sách trực tuyến' } },

  { path: 'books', component: BooksComponent, data: { mode: 'all', pageTitle: 'Tất cả sách' } },

  { path: 'books/new', component: BooksComponent, data: { mode: 'new', pageTitle: 'Sách mới' } },

  { path: 'books/bestseller', component: BooksComponent, data: { mode: 'bestseller', pageTitle: 'Sách bán chạy' } },

  { path: 'books/sale', component: BooksComponent, data: { mode: 'sale', pageTitle: 'Sách khuyến mãi' } },



  { path: 'category/:id', component: CategoryComponent, data: { pageTitle: 'Thể loại sách' } },

  { path: 'product/:id', loadChildren: () => import('./pages/product-detail/product-page.module').then(m => m.ProductPageModule), data: { pageTitle: 'Chi tiết sách' } },


  { path: 'login', component: LoginComponent, data: { pageTitle: 'Đăng nhập' } },

  { path: 'register', component: RegisterComponent, data: { pageTitle: 'Đăng ký' } },


  { path: 'payment/result', component: PaymentResultComponent, canActivate: [AuthGuard], data: { pageTitle: 'Kết quả thanh toán' } },





  { path: 'search', component: SearchComponent, data: { pageTitle: 'Tìm kiếm' } },


  { path: '', loadChildren: () => import('./pages/customer-pages.module').then(m => m.CustomerPagesModule) },

  { path: '**', component: NotFoundComponent, data: { pageTitle: 'Không tìm thấy trang' } }

];

@NgModule({
  imports: [
    RouterModule.forRoot(
      routes,
      {
        scrollPositionRestoration: 'top'
      }
    )
  ],
  exports: [RouterModule]
})
export class AppRoutingModule { }

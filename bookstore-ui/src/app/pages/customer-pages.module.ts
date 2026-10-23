import {AboutComponent} from './about/about.component';
import {ContactComponent} from './contact/contact.component';
import {CartComponent} from './cart/cart.component';
import {LucideAngularModule} from 'lucide-angular';
import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {AuthGuard} from '../core/guards/auth.guard';
import {CustomerComponentsModule} from '../shared/customer-components.module';
import {ProfileComponent} from './profile/profile.component';
import {CheckoutComponent} from './checkout/checkout.component';
import {OrdersComponent} from './orders/orders.component';
import {OrderDetailComponent} from './order-detail/order-detail.component';
import {WishlistComponent} from './wishlist/wishlist.component';
import {NotificationsComponent} from './notifications/notifications.component';
@NgModule({imports:[LucideAngularModule,CommonModule,FormsModule,CustomerComponentsModule,RouterModule.forChild([{path:'about',component:AboutComponent,data:{pageTitle:'Giới thiệu'}},{path:'contact',component:ContactComponent,data:{pageTitle:'Liên hệ'}},{path:'cart',component:CartComponent,data:{pageTitle:'Giỏ hàng'}},
{path:'profile',component:ProfileComponent,canActivate:[AuthGuard],data:{pageTitle:'Tài khoản'}},
{path:'checkout',component:CheckoutComponent,canActivate:[AuthGuard],data:{pageTitle:'Thanh toán'}},
{path:'orders/:id',component:OrderDetailComponent,canActivate:[AuthGuard],data:{pageTitle:'Chi tiết đơn hàng'}},
{path:'orders',component:OrdersComponent,canActivate:[AuthGuard],data:{pageTitle:'Đơn hàng'}},
{path:'wishlist',component:WishlistComponent,canActivate:[AuthGuard],data:{pageTitle:'Yêu thích'}},
{path:'notifications',component:NotificationsComponent,canActivate:[AuthGuard],data:{pageTitle:'Thông báo'}},
])],declarations:[AboutComponent,ContactComponent,CartComponent,ProfileComponent,CheckoutComponent,OrdersComponent,OrderDetailComponent,WishlistComponent,NotificationsComponent]})
export class CustomerPagesModule {}

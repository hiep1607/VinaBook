import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RouterModule, Routes } from '@angular/router';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

import { AppComponent } from './app.component';

import { ToastrModule } from 'ngx-toastr';
import { LoginComponent } from './login/login.component';
import { LoginModule } from './login/login.module';
import { AuthInterceptor } from './helper/AuthInterceptor';

const routes: Routes = [
  { path: 'admin', loadChildren: () => import('./admin/admin.module').then(module => module.AdminModule) },
  { path: 'login', component: LoginComponent },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', component: LoginComponent },
]

@NgModule({
  declarations: [
    AppComponent,
  ],
  imports: [
    BrowserModule,
    RouterModule.forRoot(routes),
    HttpClientModule,
    ToastrModule.forRoot({ timeOut: 2500, progressAnimation: 'increasing', closeButton: true }),
    LoginModule,
    BrowserAnimationsModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

import { ChatLauncherComponent } from './components/chatbot/chat-launcher.component';
import {CustomerComponentsModule} from './shared/customer-components.module';
import { NgModule } from '@angular/core';
import {
  LucideAngularModule,
  Bell,
  LocateFixed,
  Truck,
  ShieldCheck,
  RefreshCcw,
  Headphones,
  Search,
  ShoppingCart,
  Heart,
  User,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Clock,
  Book,
  Tag,
  Minus,
  Plus,
  X,
  Menu,
  Palette,
  MessageCircle,
  Send,
  BookOpen,
  Trash2
} from 'lucide-angular';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { HeaderComponent } from './components/header/header.component';
import { SearchComponent } from './pages/search/search.component';
import { ToastComponent } from './shared/toast/toast.component';
import { CategoryComponent } from './pages/category/category.component';
import { BooksComponent } from './pages/books/books.component';
import { NgrokSkipInterceptor } from './core/ngrok-skip.interceptor';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';
import { FooterComponent } from './components/footer/footer.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { ThemeSwitcherComponent } from './components/theme-switcher/theme-switcher.component';
import { PaymentResultComponent } from './pages/payment-result/payment-result.component';

@NgModule({
  declarations: [AppComponent, ChatLauncherComponent,
    HomeComponent,
    LoginComponent,
    RegisterComponent,
    HeaderComponent,
    SearchComponent,
    ToastComponent,
    CategoryComponent,
    BooksComponent,
    FooterComponent,
    NotFoundComponent,
    ThemeSwitcherComponent,
    PaymentResultComponent
  ],

  imports: [
    BrowserModule,
    AppRoutingModule,

    HttpClientModule,

    FormsModule,
    CustomerComponentsModule,
    LucideAngularModule.pick({
      Bell,
      LocateFixed,
    Truck,
      ShieldCheck,
      RefreshCcw,
      Headphones,
      Search,
      ShoppingCart,
      Heart,
      User,
      ChevronLeft,
      ChevronRight,
      ChevronDown,
      Clock,
      Book,
      Tag,
      Minus,
      Plus,
      X,
      Menu,
      Palette,
      MessageCircle,
      Send,
      BookOpen,
      Trash2
    }),
  ],

  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: NgrokSkipInterceptor,
      multi: true
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],

  bootstrap: [AppComponent]
})

export class AppModule { }

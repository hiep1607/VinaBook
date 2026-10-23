import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { CustomerComponentsModule } from '../../shared/customer-components.module';
import { ChatbotComponent } from './chatbot.component';
import { ChatCommerceComponent } from '../chat-commerce/chat-commerce.component';
import { BookReviewComponent } from '../book-review/book-review.component';

@NgModule({ imports: [CommonModule, FormsModule, LucideAngularModule, CustomerComponentsModule],
  declarations: [ChatbotComponent, ChatCommerceComponent, BookReviewComponent] })
export class ChatWidgetModule {}
export { ChatbotComponent };

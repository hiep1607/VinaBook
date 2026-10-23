import { Subscription } from 'rxjs';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { ChatMessage } from '../../common/ChatMessage';
import { Customer } from '../../common/Customer';
import { Notification } from '../../common/Notification';
import { CustomerService } from '../../services/customer.service';
import { NotificationService } from '../../services/notification.service';
import { SessionService } from '../../services/session.service';
import { WebSocketService } from '../../services/web-socket.service';
import { environment } from 'src/environments/environment';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit {

  user!: Customer;
  image!: string;
  name!: string;

  notifications: Notification[] = [];
  readonly storefrontUrl = environment.storefrontUrl;

  private socketSubscription?: Subscription;
  chatMessages: ChatMessage[] = [];

  constructor(private socketEvents: WebSocketService, private sessionService: SessionService, private router: Router, private customerService: CustomerService,
    private toastr: ToastrService, private notificationService: NotificationService) { }

  ngOnInit(): void {
    this.openWebSocket();
    this.getUser();
    this.getAllNotification()
  }

  getUser() {
    let email = this.sessionService.getUser();
    this.customerService.getByEmail(email).subscribe(data => {
      this.user = data as Customer;
      this.name = this.user.name;
      this.image = this.user.image;
    }, error => {
      this.toastr.error('Đã xảy ra lỗi', 'Hệ thống');
      this.sessionService.signOut();
      this.router.navigate(['/login']);
    })
  }

  getAllNotification() {
    this.notificationService.get().subscribe(data => {
      this.notifications = data as Notification[];
    })
  }

  getNotificationFalse(): number {
    let count = 0;
    for (const item of this.notifications) {
      if (!item.status) {
        count++;
      }
    }
    return count;
  }

  readed(id: number) {
    this.notificationService.readed(id).subscribe(data => {
      this.getAllNotification();
    })
  }

  readAll() {
    for (const i of this.notifications) {
      this.notificationService.readed(i.id).subscribe(data => {
      })
    }
    this.getAllNotification();
  }
  ngOnDestroy(): void { this.closeWebSocket(); }

  logOut() {
    this.closeWebSocket();
    this.sessionService.signOut();
    this.router.navigate(['/login']);
  }

  finish() {
    this.ngOnInit();
  }

  openWebSocket(): void {
    this.socketSubscription?.unsubscribe();
    this.socketSubscription = this.socketEvents.watch().subscribe(message => {
      this.toastr.info(message.message, 'Đơn hàng');
      this.getAllNotification();
    });
  }
  closeWebSocket(): void { this.socketSubscription?.unsubscribe(); this.socketSubscription = undefined; }

}

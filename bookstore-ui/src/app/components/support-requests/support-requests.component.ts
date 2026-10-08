import { Component, DoCheck, OnDestroy, OnInit } from '@angular/core';
import { AuthService } from 'src/app/services/auth.service';
import { SupportRequest, SupportRequestService } from 'src/app/services/support-request.service';
@Component({ selector: 'app-support-requests', templateUrl: './support-requests.component.html', styleUrls: ['./support-requests.component.scss'] })
export class SupportRequestsComponent implements OnInit, DoCheck, OnDestroy {
  chatId = 0;
  requests: SupportRequest[] = []; busy = false; error = ''; private owner = ''; private version = 0;
  constructor(public support: SupportRequestService, private auth: AuthService) {}
  ngOnInit(): void { this.owner = this.auth.currentUser?.email || ''; void this.load(); }
  ngDoCheck(): void { const owner = this.auth.currentUser?.email || ''; if (owner !== this.owner) { this.version++; this.owner = owner; this.requests = []; this.busy = false; void this.load(); } }
  async load(): Promise<void> {
    if (!this.owner || this.busy) return; const version = this.version, owner = this.owner; this.busy = true; this.error = '';
    try { const requests = await this.support.mine().toPromise(); if (version === this.version && owner === (this.auth.currentUser?.email || '')) this.requests = requests; }
    catch (error) { if (version === this.version) this.error = (error as any)?.error?.message || 'Chưa tải được yêu cầu đã gửi. Bạn thử lại nhé.'; }
    finally { if (version === this.version) this.busy = false; }
  }
  ngOnDestroy(): void { this.version++; this.requests = []; }
}

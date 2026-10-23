import { AfterViewInit, Compiler, Component, DoCheck, ElementRef, Injector, NgModuleRef, OnDestroy, ViewChild, ViewContainerRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { ChatCheckoutService } from '../../services/chat-checkout.service';
import { FabCollisionService } from '../../services/fab-collision.service';

@Component({ selector: 'app-chat-launcher',
  template: `<button #launcher class="chat-fab" [hidden]="loaded" [disabled]="loading" (click)="open()" aria-label="Mở trợ lý VinaBook" [attr.aria-busy]="loading">
    <lucide-icon name="message-circle" [size]="23"></lucide-icon></button>
    <span class="launcher-error" *ngIf="error" role="status">{{ error }}</span><ng-template #widget></ng-template>`,
  styles: [`.chat-fab{position:fixed;right:24px;bottom:calc(24px + var(--fab-lift,0px));width:56px;height:56px;border:1px solid rgba(255,255,255,.22);border-radius:16px;display:grid;place-items:center;color:#fff;background:var(--c-primary);box-shadow:0 14px 34px rgba(7,31,25,.24);cursor:pointer;z-index:1000}.chat-fab[hidden]{display:none}.chat-fab:focus-visible{outline:3px solid var(--c-primary);outline-offset:4px}.launcher-error{position:fixed;right:24px;bottom:88px;max-width:260px;padding:12px;background:var(--c-surface,#fff);border:1px solid var(--c-border,#ddd);border-radius:12px;z-index:1000}@media(max-width:600px){.chat-fab{right:16px;bottom:calc(16px + var(--fab-lift,0px))}}`] })
export class ChatLauncherComponent implements AfterViewInit, DoCheck, OnDestroy {
  @ViewChild('widget', {read: ViewContainerRef, static: true}) host!: ViewContainerRef;
  @ViewChild('launcher', {static: true}) button!: ElementRef<HTMLElement>;
  loaded = false;
  loading = false;
  error = '';
  private destroyed = false;
  private module?: NgModuleRef<unknown>;
  private collisionSub?: Subscription;
  constructor(private compiler: Compiler, private injector: Injector, private collision: FabCollisionService,
    private auth: AuthService, private checkout: ChatCheckoutService) {}
  ngAfterViewInit(): void {
    this.collisionSub = this.collision.watch(() => this.loaded ? null : this.button.nativeElement)
      .subscribe(lift => this.button.nativeElement.style.setProperty('--fab-lift', `${lift}px`));
  }
  ngDoCheck(): void {
    if (!this.loaded && !this.loading && this.auth.currentUser?.token && this.checkout.hasPurchaseIntent('chat')) void this.open();
  }
  async open(): Promise<void> {
    if (this.loading || this.loaded) return;
    this.loading = true; this.error = '';
    try {
      const widget = await import('./chat-widget.module');
      const factory = await this.compiler.compileModuleAsync(widget.ChatWidgetModule);
      if (this.destroyed) return;
      this.module = factory.create(this.injector);
      const component = this.host.createComponent(this.module.componentFactoryResolver.resolveComponentFactory(widget.ChatbotComponent), undefined, this.module.injector);
      component.changeDetectorRef.detectChanges();
      if (!component.instance.open) component.instance.toggle();
      this.loaded = true;
    } catch { this.error = 'Chưa mở được trợ lý. Bạn bấm lại để thử nhé.'; }
    finally { this.loading = false; }
  }
  ngOnDestroy(): void { this.destroyed = true; this.collisionSub?.unsubscribe(); this.host.clear(); this.module?.destroy(); }
}

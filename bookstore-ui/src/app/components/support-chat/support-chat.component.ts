import { Component, DoCheck, Input, OnChanges, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Subscription, timer } from 'rxjs';
import { catchError, exhaustMap, timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { AuthService } from 'src/app/services/auth.service';
interface Message { id:number; content:string; staff:boolean; mine:boolean; createdAt:string; }
@Component({selector:'app-support-chat',templateUrl:'./support-chat.component.html',styleUrls:['./support-chat.component.scss']})
export class SupportChatComponent implements OnChanges, DoCheck, OnDestroy {
  @Input() requestId=0; @Input() closed=false;
  messages:Message[]=[]; draft=''; error=''; sending=false; uncertain=false; loading=true; hasOlder=false;
  private poll?:Subscription; private sendSub?:Subscription; private olderSub?:Subscription;
  private version=0; private owner=''; private pending?:{clientKey:string;content:string};
  constructor(private http:HttpClient , private auth:AuthService) {}
  private identity():string { return this.auth.currentUser?.token || ''; }
  private url():string { return environment.apiUrl+'/api/support/me/'+this.requestId+'/messages'; }
  ngOnChanges():void {
    this.stop(); this.owner=this.identity(); this.messages=[]; this.draft=''; this.error=''; this.pending=undefined; this.uncertain=false; this.loading=true; this.hasOlder=false;
    if(!this.requestId || !this.owner)return; const version=this.version;
    this.poll=timer(0,3000).pipe(exhaustMap(()=>this.http.get<Message[]>(this.url(),{params:{after:String(this.messages[this.messages.length-1]?.id || 0)}}).pipe(timeout(10000),catchError(error=>{
      if(this.current(version)){this.loading=false;this.error=error?.error?.message || 'Kết nối tạm gián đoạn. Tin nhắn sẽ được cập nhật khi có mạng.';if([401,403,404].includes(error.status)){this.messages=[];this.stop();}}
      return EMPTY;
    })))).subscribe(rows=>{if(this.current(version)){this.loading=false;this.error='';if(!this.messages.length)this.hasOlder=rows.length===50;this.merge(rows);}});
  }
  ngDoCheck():void {if(this.owner && this.owner!==this.identity()){this.stop();this.messages=[];this.draft='';this.pending=undefined;this.uncertain=false;this.owner='';}}
  private current(version:number):boolean {return version===this.version && !!this.owner && this.owner===this.identity();}
  private merge(rows:Message[]):void {const map=new Map(this.messages.map(row=>[row.id,row]));rows.forEach(row=>map.set(row.id,row));this.messages=[...map.values()].sort((a,b)=>a.id-b.id);}
  older():void {
    if(!this.hasOlder || this.loading || !this.messages.length)return; const version=this.version;this.loading=true;
    this.olderSub=this.http.get<Message[]>(this.url(),{params:{before:String(this.messages[0].id)}}).pipe(timeout(10000)).subscribe({
      next:rows=>{if(this.current(version)){this.loading=false;this.hasOlder=rows.length===50;this.merge(rows);}},
      error:()=>{if(this.current(version)){this.loading=false;this.error='Chưa tải được tin nhắn cũ. Bạn thử lại nhé.';}}
    });
  }
  send():void {
    if(this.sending || this.closed || !this.owner || !this.current(this.version))return;
    if(!this.uncertain){const content=this.draft.trim();if(!content || content.length>2000)return;this.pending={clientKey:'message-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2),content};}
    if(!this.pending)return;this.sending=true;this.error='';const version=this.version;
    this.sendSub=this.http.post<Message>(this.url(),this.pending).pipe(timeout(15000)).subscribe({
      next:row=>{if(this.current(version)){this.merge([row]);this.draft='';this.pending=undefined;this.sending=false;this.uncertain=false;}},
      error:error=>{if(this.current(version)){this.sending=false;this.uncertain=!error.status || error.status>=500;this.error=error?.error?.message || 'Chưa xác nhận được tin đã gửi. Bạn kiểm tra/gửi lại cùng tin để tránh gửi trùng nhé.';if(!this.uncertain)this.pending=undefined;}}
    });
  }
  track(_index:number,row:Message):number{return row.id;}
  private stop():void {this.version++;this.poll?.unsubscribe();this.sendSub?.unsubscribe();this.olderSub?.unsubscribe();this.sending=false;}
  ngOnDestroy():void{this.stop();this.messages=[];this.draft='';this.pending=undefined;}
}

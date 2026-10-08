import {Component,Input,Output,EventEmitter,OnChanges,OnDestroy} from '@angular/core';
import {HttpClient} from '@angular/common/http';import {Subscription} from 'rxjs';import {timeout} from 'rxjs/operators';
import {environment} from 'src/environments/environment';
export interface AppliedVoucher {code:string;discount:number;}
@Component({selector:'app-voucher-input',templateUrl:'./voucher-input.component.html',styles:[':host{display:block;margin:16px 0} .entry{display:flex;gap:8px}input{min-width:0;flex:1;padding:10px;border:1px solid var(--border,#ddd);border-radius:8px}button{padding:8px 12px}p{margin:8px 0;font-size:14px}']})
export class VoucherInputComponent implements OnChanges,OnDestroy {
 @Input() items:{id:number;quantity:number;price?:number}[]=[];@Input() disabled=false;@Output() applied=new EventEmitter<AppliedVoucher>();@Output() busyChange=new EventEmitter<boolean>();
 code='';error='';busy=false;discount=0;private fingerprint='';private request?:Subscription;private version=0;
 constructor(private http:HttpClient){}
 ngOnChanges():void{const key=JSON.stringify(this.items.map(i=>[i.id,i.quantity,i.price]));if(key!==this.fingerprint){this.fingerprint=key;this.remove();}}
 ngOnDestroy():void{this.version++;this.request?.unsubscribe();}
 remove():void{if(this.disabled)return;this.version++;this.request?.unsubscribe();this.busy=false;this.busyChange.emit(false);this.discount=0;this.code='';this.error='';this.applied.emit({code:'',discount:0});}
 edit(value:string):void{this.code=value;this.discount=0;this.error='';this.applied.emit({code:'',discount:0});}
 apply():void{if(this.busy||this.disabled)return;const code=this.code.trim().toUpperCase();if(!/^[A-Z0-9_-]{3,32}$/.test(code)){this.error='Bạn nhập mã gồm 3–32 chữ, số hoặc dấu gạch nhé.';return;}this.busy=true;this.busyChange.emit(true);this.error='';const version=++this.version;
  this.request=this.http.post<{code:string;discount:number;subtotal:number}>(environment.apiUrl+'/api/vouchers/me/preview',{code,items:this.items.map(i=>({productId:i.id,quantity:i.quantity}))}).pipe(timeout(15000)).subscribe({next:data=>{if(version!==this.version)return;this.busy=false;this.busyChange.emit(false);const local=this.items.reduce((sum,i)=>sum+(i.price||0)*i.quantity,0);if(this.items.every(i=>i.price!=null)&&local!==data.subtotal){this.error='Giá sách đã thay đổi. Bạn tải lại giỏ để kiểm tra tổng tiền nhé.';this.discount=0;this.applied.emit({code:'',discount:0});return;}this.code=data.code;this.discount=data.discount;this.applied.emit(data);},error:e=>{if(version!==this.version)return;this.busy=false;this.busyChange.emit(false);this.discount=0;this.applied.emit({code:'',discount:0});this.error=e?.error?.message||'Mình chưa kiểm tra được mã. Bạn thử lại hoặc tiếp tục không dùng mã nhé.';}});
 }
}

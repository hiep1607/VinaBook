import {Component,Input,OnChanges,OnDestroy,DoCheck} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Subscription} from 'rxjs';
import {timeout} from 'rxjs/operators';
import {environment} from 'src/environments/environment';
@Component({selector:'app-payment-refunds',templateUrl:'./payment-refunds.component.html',styleUrls:['../customer-workspace.css','./payment-refunds.component.css']})
export class PaymentRefundsComponent implements OnChanges,OnDestroy,DoCheck {
 @Input() orderId=0;data:any;amount=0;reason='';error='';busy=false;review=false;uncertain=false;
 reference='';result='SUCCEEDED';reconcileId=0;private key='';private request?:Subscription;
 private owner=sessionStorage.getItem('auth-token')||'';
 ngDoCheck():void{const key=sessionStorage.getItem('auth-token')||'';if(key!==this.owner){this.owner=key;this.ngOnChanges();}}
 private get url():string{return environment.apiUrl+'/api/payments/admin/orders/'+this.orderId+'/refunds';}
 constructor(private http:HttpClient){}
 ngOnChanges():void{this.request?.unsubscribe();this.busy=false;this.amount=0;this.reason='';this.reconcileId=0;this.reference='';this.data=null;this.review=false;this.uncertain=false;this.key='';this.load();}
 ngOnDestroy():void{this.request?.unsubscribe();}
 load():void {if(this.busy)return;this.busy=true;this.error='';this.request=this.http.get(this.url).pipe(timeout(15000)).subscribe({next:data=>{this.data=data;this.busy=false;},error:e=>{this.busy=false;this.error=e?.error?.message||'Chưa tải được dữ liệu hoàn tiền. Bạn tải lại nhé.';}});}
 prepare():void {if(this.busy||!this.data?.available||!Number.isSafeInteger(this.amount)||this.amount<this.data.minimum||this.amount>this.data.remaining||this.reason.trim().length<5){this.error='Bạn nhập số tiền trong hạn mức và lý do ít nhất 5 ký tự nhé.';return;}this.error='';this.review=true;this.key='refund-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);}
 confirm():void {if(this.busy||!this.review)return;this.busy=true;this.error='';this.request=this.http.post(this.url,{amount:this.amount,reason:this.reason.trim(),requestKey:this.key,confirmed:true}).pipe(timeout(45000)).subscribe({next:data=>{this.data=data;this.busy=false;this.review=false;this.uncertain=false;this.reason='';this.amount=0;},error:e=>{this.busy=false;this.uncertain=!e.status||e.status>=500;this.error=e?.error?.message||(this.uncertain?'Chưa nhận được kết quả. Giữ nguyên yêu cầu và tải lại hoặc gửi lại cùng yêu cầu để kiểm tra, tránh hoàn tiền hai lần.':'Chưa gửi được yêu cầu. Bạn kiểm tra lại nhé.');}});}
 reconcile():void{if(this.busy||!this.reconcileId||this.reference.trim().length<5)return;this.busy=true;this.request=this.http.post(this.url+'/'+this.reconcileId+'/reconcile',{status:this.result,reference:this.reference.trim(),confirmed:true}).pipe(timeout(15000)).subscribe({next:data=>{this.data=data;this.reconcileId=0;this.busy=false;this.reference='';},error:e=>{this.busy=false;this.error=e?.error?.message||'Chưa lưu được kết quả đối soát.';}});}
 label(status:string):string{return ({SUCCEEDED:'Đã hoàn tiền',FAILED:'Không hoàn thành',PENDING:'Cổng đang xử lý',PROCESSING:'Đang gửi yêu cầu',UNKNOWN:'Cần đối soát'} as any)[status]||status;}
}

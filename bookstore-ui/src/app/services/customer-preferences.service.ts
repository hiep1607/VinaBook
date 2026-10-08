import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
export interface SavedAddress {id:number;label:string;name:string;phone:string;street:string;isDefault:boolean;}
export interface Preferences {addresses:SavedAddress[];avatar:string;wishlistProductIds:number[];revision:number;ownerEmail?:string;}
@Injectable({providedIn:'root'})
export class CustomerPreferencesService implements OnDestroy {
  readonly changes$=new Subject<Preferences>(); error='';
  private api=environment.apiUrl+'/api/preferences/me'; private ownerKey='';private data?:Preferences;private loadedAt=0;private attemptedAt=0;
  private loading?:{key:string;promise:Promise<Preferences>};private queue:Promise<unknown>=Promise.resolve();
  private onFocus=()=>{const user=this.user();if(user?.email)void this.refresh(user.email,true).catch(()=>{});};
  constructor(private http:HttpClient) {window.addEventListener('focus',this.onFocus);}
  ngOnDestroy():void {window.removeEventListener('focus',this.onFocus);this.changes$.complete();}
  private user():any {try{return JSON.parse(localStorage.getItem('currentUser') || 'null');}catch{return null;}}
  private key(email:string):string {const user=this.user();if(!user?.token || user.email!==email)throw new Error('Bạn đăng nhập đúng tài khoản để đồng bộ nhé.');return email+':'+user.token;}
  private read<T>(key:string,fallback:T):T {try{return JSON.parse(localStorage.getItem(key) || 'null') ?? fallback;}catch{return fallback;}}
  cached(email:string):Preferences {
    let key:string;try{key=this.key(email);}catch{return {addresses:[],avatar:'',wishlistProductIds:[],revision:0};}
    if(key!==this.ownerKey){this.ownerKey=key;this.data=undefined;this.loadedAt=0;this.attemptedAt=0;this.error='';}
    if(Date.now()-this.attemptedAt>15000)void this.refresh(email).catch(()=>{});
    return this.data || {addresses:this.read('addresses_'+email,[]),avatar:localStorage.getItem('avatar_'+email)||'',wishlistProductIds:this.read('wishlist_'+email,[]),revision:0};
  }
  private install(email:string,key:string,data:Preferences):Preferences {
    if(this.key(email)!==key)throw new Error('Tài khoản đã đổi. Bạn thực hiện lại trên tài khoản hiện tại nhé.');
    this.ownerKey=key;this.data=data;this.loadedAt=Date.now();this.error='';
    localStorage.setItem('addresses_'+email,JSON.stringify(data.addresses));localStorage.setItem('wishlist_'+email,JSON.stringify(data.wishlistProductIds));
    if(data.avatar)localStorage.setItem('avatar_'+email,data.avatar);else localStorage.removeItem('avatar_'+email);
    this.changes$.next({...data,ownerEmail:email});return data;
  }
  async refresh(email:string,force=false):Promise<Preferences> {
    const key=this.key(email);if(key!==this.ownerKey){this.ownerKey=key;this.data=undefined;this.loadedAt=0;this.attemptedAt=0;this.error="";}if(this.loading?.key===key)return this.loading.promise;
    if(!force && this.data && this.ownerKey===key && Date.now()-this.loadedAt<15000)return this.data;
    this.attemptedAt=Date.now();
    const promise=(async()=>{
      const marker='vinabook_preferences_import:'+encodeURIComponent(email);
      let migrationKey=localStorage.getItem(marker);const migrated=localStorage.getItem(marker+':done')==='true';
      if(!migrationKey){migrationKey='import-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);localStorage.setItem(marker,migrationKey);}
      let data:Preferences;
      if(!migrated){
        const legacy={migrationKey,addresses:this.read<SavedAddress[]>('addresses_'+email,[]),avatar:localStorage.getItem('avatar_'+email)||'',wishlistProductIds:this.read<number[]>('wishlist_'+email,[])};
        // Preserve the original input before server cache replaces the browser keys.
        if(!localStorage.getItem(marker+':backup'))localStorage.setItem(marker+':backup',JSON.stringify(legacy));
        const payload=this.read<any>(marker+':backup',legacy);
        if(payload.avatar.length>360000 || (payload.avatar&&!/^data:image\/(png|jpeg|gif|webp);base64,/.test(payload.avatar))){payload.avatar='';localStorage.setItem(marker+':notice','Ảnh cũ chưa hỗ trợ hoặc quá lớn. Bản gốc vẫn được giữ trong dữ liệu sao lưu trình duyệt; bạn chọn lại ảnh PNG/JPEG tối đa 256 KB trong Hồ sơ nhé.');}
        data=await this.http.post<Preferences>(this.api+'/migrate',payload).pipe(timeout(15000)).toPromise();
        if(this.key(email)!==key)throw new Error('Tài khoản đã đổi.');localStorage.setItem(marker+':done','true');
      }else data=await this.http.get<Preferences>(this.api).pipe(timeout(15000)).toPromise();
      return this.install(email,key,data);
    })();this.loading={key,promise};
    try{return await promise;}catch(error){if(this.ownerKey===key || !this.ownerKey)this.error=(error as any)?.error?.message || (error as Error).message || 'Chưa đồng bộ được dữ liệu. Bạn thử lại nhé.';throw error;}
    finally{if(this.loading?.promise===promise)this.loading=undefined;}
  }
  mutate(email:string,kind:'addresses'|'avatar'|'wishlist',value:any,remove=false):Promise<Preferences> {
    const work=this.queue.catch(()=>{}).then(async()=>{
      const current=await this.refresh(email);const key=this.key(email);
      try{let result:Preferences;
        if(kind==='wishlist'){const url=this.api+'/wishlist/'+value;result=await (remove?this.http.delete<Preferences>(url):this.http.put<Preferences>(url,{})).pipe(timeout(15000)).toPromise();}
        else result=await this.http.put<Preferences>(this.api+'/'+kind,{revision:current.revision,[kind]:typeof value==='function'?value(current):value}).pipe(timeout(15000)).toPromise();
        return this.install(email,key,result);
      }catch(error){if((error as any).status===409)await this.refresh(email,true).catch(()=>{});throw error;}
    });this.queue=work;return work;
  }
}

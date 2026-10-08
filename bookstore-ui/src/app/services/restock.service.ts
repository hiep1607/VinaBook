import {Injectable} from '@angular/core';import {HttpClient} from '@angular/common/http';import {timeout} from 'rxjs/operators';import {environment} from 'src/environments/environment';
export interface StockNotice {productId:number;name:string;state:string;seen:boolean;availableAt:string|null;inStock:boolean;}
@Injectable({providedIn:'root'}) export class RestockService {
 private api=environment.apiUrl+'/api/restock/me';constructor(private http:HttpClient){}
 list(){return this.http.get<StockNotice[]>(this.api).pipe(timeout(15000));}
 get(id:number){return this.http.get<StockNotice>(this.api+'/'+id).pipe(timeout(15000));}
 watch(id:number){return this.http.put<StockNotice>(this.api+'/'+id,{}).pipe(timeout(15000));}
 remove(id:number){return this.http.delete<void>(this.api+'/'+id).pipe(timeout(15000));}
 seen(id:number){return this.http.put<void>(this.api+'/'+id+'/seen',{}).pipe(timeout(15000));}
}

import { Injectable } from '@angular/core';
import { CustomerPreferencesService } from './customer-preferences.service';
@Injectable({providedIn:'root'})
export class WishlistService {
  constructor(private preferences:CustomerPreferencesService) {}
  get syncError():string{return this.preferences.error;}
  refresh(email:string){return this.preferences.refresh(email,true);}
  getIds(email:string):number[]{return [...this.preferences.cached(email).wishlistProductIds];}
  isInWishlist(email:string,id:number):boolean{return this.getIds(email).includes(id);}
  getCount(email:string):number{return this.getIds(email).length;}
  async toggleWishlist(email:string,id:number):Promise<boolean>{await this.preferences.refresh(email);const add=!this.isInWishlist(email,id);await this.preferences.mutate(email,'wishlist',id,!add);return add;}
  async addToWishlist(email:string,id:number):Promise<void>{await this.preferences.mutate(email,'wishlist',id);}
  async removeFromWishlist(email:string,id:number):Promise<void>{await this.preferences.mutate(email,'wishlist',id,true);}
}

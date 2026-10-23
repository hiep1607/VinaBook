import { normalizePhone, shippingError } from '../shared/shipping-validation';
import { Injectable } from '@angular/core';
import { CustomerPreferencesService, SavedAddress } from './customer-preferences.service';
@Injectable({providedIn:'root'})
export class AddressService {
  constructor(private preferences:CustomerPreferencesService){}
  refresh(email:string){return this.preferences.refresh(email,true);}
  getAddresses(email:string):SavedAddress[]{return this.preferences.cached(email).addresses.map(a=>({...a,phone:normalizePhone(a.phone)}));}
  async saveAddresses(email:string,addresses:SavedAddress[]):Promise<void>{await this.preferences.mutate(email,'addresses',addresses);}
  async addAddress(email:string,form:any):Promise<SavedAddress>{const error=shippingError(form.name,form.phone,form.street);if(error)throw new Error(error);let address!:SavedAddress;await this.preferences.mutate(email,'addresses',(current:any)=>{const addresses=current.addresses;let id=Date.now();while(addresses.some((a:SavedAddress)=>a.id===id))id++;address={id,label:form.label?.trim()||'Địa chỉ',name:form.name.trim(),phone:normalizePhone(form.phone),street:form.street.trim(),isDefault:!addresses.length};return [...addresses,address];});return address;}
  async updateAddress(email:string,id:number,form:any):Promise<void>{const error=shippingError(form.name,form.phone,form.street);if(error)throw new Error(error);await this.preferences.mutate(email,'addresses',(current:any)=>current.addresses.map((a:SavedAddress)=>a.id===id?{...a,label:form.label?.trim()||'Địa chỉ',name:form.name.trim(),phone:normalizePhone(form.phone),street:form.street.trim()}:a));}
  async deleteAddress(email:string,id:number):Promise<void>{await this.preferences.mutate(email,'addresses',(current:any)=>{const addresses=current.addresses.filter((a:SavedAddress)=>a.id!==id).map((a:SavedAddress)=>({...a}));if(addresses.length && !addresses.some((a:SavedAddress)=>a.isDefault))addresses[0].isDefault=true;return addresses;});}
  async setDefault(email:string,id:number):Promise<void>{await this.preferences.mutate(email,'addresses',(current:any)=>current.addresses.map((a:SavedAddress)=>({...a,isDefault:a.id===id})));}
  getDefault(email:string):SavedAddress|undefined{return this.getAddresses(email).find(a=>a.isDefault);}
}

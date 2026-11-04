import { Injectable,signal } from '@angular/core';
export interface CartItem { productId:string;name:string;code:string;unit:string;quantity:string;price:string; }
@Injectable({providedIn:'root'})
export class CartService {
  readonly items=signal<CartItem[]>(this.restore());
  private restore():CartItem[]{try{const rows:unknown=JSON.parse(sessionStorage.getItem('ferragens.cart.v1')??'[]');return Array.isArray(rows)?rows.filter((r:any)=>typeof r.productId==='string'&&typeof r.quantity==='string'&&/^\d+([.]\d{1,4})?$/.test(r.quantity)).slice(0,100):[];}catch{return [];}}
  save(){sessionStorage.setItem('ferragens.cart.v1',JSON.stringify(this.items()));}
  add(item:CartItem){this.items.update(rows=>rows.some(r=>r.productId===item.productId)?rows.map(r=>r.productId===item.productId?{...r,quantity:item.quantity}:r):[...rows,item]);this.save();}
  update(id:string,value:string){this.items.update(rows=>rows.map(r=>r.productId===id?{...r,quantity:value.replace(',','.')}:r));this.save();}
  remove(id:string){this.items.update(rows=>rows.filter(r=>r.productId!==id));this.save();}clear(){this.items.set([]);this.save();}
}

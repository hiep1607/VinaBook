import { Order } from "./order";
import { Product } from "./product";

export class OrderDetail {
    'orderDetailId':number;
    'quantity':number;
    'price':number;
    'product':Product;
    'order':Order;
}

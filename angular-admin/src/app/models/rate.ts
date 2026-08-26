import { Customer } from "./customer";
import { OrderDetail } from "./order-detail";
import { Product } from "./product";

export class Rate {
    'id': number;
    'rating': number;
    'comment': string;
    'rateDate': Date;
    'user': Customer;
    'product': Product;
    'orderDetail': OrderDetail;
}

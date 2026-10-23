package vn.fs.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ShippingDetails {
    public static final String INPUT_PHONE="[+0-9 .()-]{9,20}";
    public record Delivery(String recipient,String phone,String street) {}
    public static String normalizePhone(String input) {
        String phone=input==null?"":input.replaceAll("[\\s.()-]","");
        return phone.startsWith("+84")?"0"+phone.substring(3):phone;
    }
    public static Delivery require(String recipient,String phone,String street) {
        String name=recipient==null?"":recipient.trim(),address=street==null?"":street.trim(),number=normalizePhone(phone);
        if(name.isBlank() || name.length()>80)throw invalid("Tên người nhận cần từ 1 đến 80 ký tự.");
        if(!number.matches("0[0-9]{9}"))throw invalid("Số điện thoại cần đủ 10 chữ số, bắt đầu bằng 0 hoặc dùng mã +84.");
        if(address.length()<5 || address.length()>255)throw invalid("Địa chỉ giao hàng cần từ 5 đến 255 ký tự.");
        return new Delivery(name,number,address);
    }
    private static ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}

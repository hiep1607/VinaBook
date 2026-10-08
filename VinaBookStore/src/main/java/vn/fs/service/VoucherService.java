package vn.fs.service;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
@Service
public class VoucherService {
 private final VoucherRepository vouchers;private final VoucherUseRepository uses;private final CurrentUserAccess access;
 public VoucherService(VoucherRepository vouchers,VoucherUseRepository uses,CurrentUserAccess access){this.vouchers=vouchers;this.uses=uses;this.access=access;}
 private Long owner(){Long id=access.currentUserId();if(id==null)throw error(HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để mình kiểm tra ưu đãi đúng tài khoản nhé.");return id;}
 public static String code(String code){if(code==null||code.isBlank())return "";String normalized=code.trim().toUpperCase(Locale.ROOT);if(!normalized.matches("[A-Z0-9_-]{3,32}"))throw error(HttpStatus.BAD_REQUEST,"Mã giảm giá gồm 3–32 chữ, số, dấu gạch ngang hoặc gạch dưới.");return normalized;}
 @Transactional(readOnly=true) public long preview(String code,long subtotal){if(code(code).isEmpty())return 0;Long owner=owner();return amount(vouchers.findByCode(code(code)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Mình chưa tìm thấy mã này. Bạn kiểm tra lại mã nhé.")),subtotal,owner);}
 @Transactional public Voucher reserve(String code,long subtotal,long expected){if(code(code).isEmpty()){if(expected!=0)throw error(HttpStatus.CONFLICT,"Ưu đãi đã thay đổi. Bạn xem lại tổng tiền nhé.");return null;}
  Voucher voucher=vouchers.lockByCode(code(code)).orElseThrow(()->error(HttpStatus.CONFLICT,"Mã giảm giá không còn khả dụng."));if(amount(voucher,subtotal,owner())!=expected)throw error(HttpStatus.CONFLICT,"Ưu đãi đã thay đổi. Bạn xem lại báo giá trước khi đặt nhé.");return voucher;
 }
 public void record(Voucher voucher,Long orderId,long discount){if(voucher==null)return;VoucherUse use=new VoucherUse();use.setVoucherId(voucher.getId());use.setOwnerId(owner());use.setOrderId(orderId);use.setDiscount(discount);uses.save(use);}
 long amount(Voucher voucher,long subtotal,Long owner){Instant now=Instant.now();if(!voucher.isActive()||voucher.getStartsAt()==null||voucher.getExpiresAt()==null||now.isBefore(voucher.getStartsAt())||!now.isBefore(voucher.getExpiresAt()))throw error(HttpStatus.CONFLICT,"Mã này chưa bắt đầu hoặc đã hết hạn. Bạn chọn ưu đãi khác nhé.");
  if(subtotal<voucher.getMinimumSubtotal())throw error(HttpStatus.CONFLICT,"Mã áp dụng cho tiền sách từ "+voucher.getMinimumSubtotal()+"đ. Bạn có thể thêm sách hoặc bỏ mã để tiếp tục nhé.");
  if(uses.countByVoucherId(voucher.getId())>=voucher.getUsageLimit()||uses.countByVoucherIdAndOwnerId(voucher.getId(),owner)>=voucher.getPerUserLimit())throw error(HttpStatus.CONFLICT,"Mã đã hết lượt sử dụng cho đơn hoặc tài khoản này.");
  long discount="PERCENT".equals(voucher.getKind())?Math.multiplyExact(subtotal,voucher.getValue())/100:voucher.getValue();if(voucher.getMaximumDiscount()>0)discount=Math.min(discount,voucher.getMaximumDiscount());return Math.min(discount,subtotal);
 }
 private void admin(){if(!access.isAdmin())throw error(HttpStatus.FORBIDDEN,"Chỉ nhân viên được quản lý mã giảm giá.");}
 @Transactional(readOnly=true) public List<Voucher> list(){admin();return vouchers.findTop100ByOrderByIdDesc();}
 @Transactional public Voucher save(Voucher input){admin();if(input==null)throw error(HttpStatus.BAD_REQUEST,"Thiếu thông tin mã.");String code=code(input.getCode());if(code.isEmpty()||!Set.of("FIXED","PERCENT").contains(input.getKind()==null?"":input.getKind())||input.getValue()<1||("PERCENT".equals(input.getKind())&&input.getValue()>100)||input.getValue()>50000000||input.getMaximumDiscount()<0||input.getMaximumDiscount()>50000000||input.getMinimumSubtotal()<0||input.getMinimumSubtotal()>50000000||input.getUsageLimit()<1||input.getUsageLimit()>1000000||input.getPerUserLimit()<1||input.getPerUserLimit()>input.getUsageLimit()||input.getStartsAt()==null||input.getExpiresAt()==null||!input.getExpiresAt().isAfter(input.getStartsAt()))throw error(HttpStatus.BAD_REQUEST,"Bạn kiểm tra giá trị, hạn mức và thời gian áp dụng của mã nhé.");
  Voucher existing=input.getId()==null?null:vouchers.findById(input.getId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy mã."));if(existing!=null&&!existing.getCode().equals(code))throw error(HttpStatus.CONFLICT,"Mã đã tạo không đổi tên; bạn có thể tắt mã và tạo mã mới.");
  if(existing==null&&vouchers.findByCode(code).isPresent())throw error(HttpStatus.CONFLICT,"Mã đã tồn tại. Bạn chỉnh sửa mã đang có nhé.");input.setCode(code);return vouchers.save(input);
 }
 private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}

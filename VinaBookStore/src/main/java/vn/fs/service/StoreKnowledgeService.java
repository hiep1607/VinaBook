package vn.fs.service;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.entity.StorePolicy;
import vn.fs.entity.StoreSettings;
import vn.fs.repository.StorePolicyRepository;
import vn.fs.repository.StoreSettingsRepository;

/** Shared source for checkout calculation, public policy copy and chat answers. */
@Service
public class StoreKnowledgeService {
    private static final Set<String> KEYS = Set.of("returns","cancellation","support");
    private final StoreSettingsRepository settingsRepo;
    private final StorePolicyRepository policyRepo;
    private final PaymentGatewayService payments;
    public StoreKnowledgeService(StoreSettingsRepository settingsRepo, StorePolicyRepository policyRepo,
            PaymentGatewayService payments) {
        this.settingsRepo=settingsRepo; this.policyRepo=policyRepo; this.payments=payments;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (!settingsRepo.existsById(1L)) settingsRepo.save(new StoreSettings());
        Map<String,String> titles=Map.of("returns","Đổi trả","cancellation","Hủy đơn","support","Liên hệ hỗ trợ");
        for(String key:KEYS) if(policyRepo.findTopByPolicyKeyOrderByVersionDesc(key).isEmpty()) {
            StorePolicy policy=new StorePolicy(); policy.setPolicyKey(key); policy.setVersion(1);
            policy.setTitle(titles.get(key)); policy.setBody(defaultText(key));
            policy.setEffectiveAt(LocalDateTime.now());policy.setUpdatedAt(LocalDateTime.now());
            policy.setApproved(true); policyRepo.save(policy);
        }
    }
    public StoreSettings settings() { return settingsRepo.findById(1L).orElseGet(StoreSettings::new); }
    public long shipping(long subtotal) {
        StoreSettings s=settings();
        return subtotal>0 && subtotal<s.getFreeShippingThreshold()?s.getShippingFee():0;
    }
    public List<StorePolicy> activePolicies() {
        LocalDateTime now=LocalDateTime.now();
        Map<String,StorePolicy> active=new TreeMap<>();
        for(StorePolicy policy:policyRepo.findAll()) {
            if(!policy.isApproved() || policy.getEffectiveAt()==null || policy.getEffectiveAt().isAfter(now)) continue;
            StorePolicy previous=active.get(policy.getPolicyKey());
            if(previous==null || policy.getEffectiveAt().isAfter(previous.getEffectiveAt())
                    || (policy.getEffectiveAt().equals(previous.getEffectiveAt()) && policy.getVersion()>previous.getVersion()))
                active.put(policy.getPolicyKey(),policy);
        }
        return new ArrayList<>(active.values());
    }
    public String text(String key) {
        if("shipping".equals(key)) {
            StoreSettings s=settings();
            return "Phí giao hàng " + money(s.getShippingFee()) + ", miễn phí khi tiền sách từ "
                    + money(s.getFreeShippingThreshold()) + ". Thời gian giao dự kiến "
                    + s.getDeliveryMinDays()+"–"+s.getDeliveryMaxDays()+" ngày làm việc.";
        }
        if("payment".equals(key)) {
            List<String> available=new ArrayList<>();
            payments.availableMethods().forEach((code,value)->{
                if(value instanceof Map<?,?> method && Boolean.TRUE.equals(method.get("available")))
                    available.add(Objects.toString(method.get("label"),code));
            });
            return "Các phương thức thanh toán đang khả dụng: "+String.join(", ",available)+".";
        }
        return activePolicies().stream().filter(p->p.getPolicyKey().equals(key)).map(StorePolicy::getBody)
                .findFirst().orElseGet(()->defaultText(key));
    }
    private String defaultText(String key) {
        return switch(key) {
            case "returns" -> "Sách lỗi in, rách hoặc giao sai được hỗ trợ đổi trả trong 7 ngày kể từ khi nhận hàng. Bạn giữ hóa đơn và liên hệ trang Hỗ trợ.";
            case "cancellation" -> "Đơn COD chưa thanh toán và đang chờ xử lý có thể hủy; đơn đang giao, hoàn thành hoặc thanh toán trực tuyến cần liên hệ cửa hàng.";
            case "support" -> "Bạn có thể gửi yêu cầu tại trang Liên hệ hoặc email hotro@vinabook.vn. Không gửi mật khẩu, OTP hay thông tin thẻ.";
            default -> "Chưa có nội dung chính sách được xác nhận cho mục này.";
        };
    }
    @Transactional public StoreSettings updateSettings(StoreSettings incoming) {
        if(incoming.getShippingFee()<0 || incoming.getFreeShippingThreshold()<0
                || incoming.getDeliveryMinDays()<1 || incoming.getDeliveryMaxDays()<incoming.getDeliveryMinDays())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cấu hình giao hàng không hợp lệ.");
        StoreSettings saved=settings(); saved.setShippingFee(incoming.getShippingFee());
        saved.setFreeShippingThreshold(incoming.getFreeShippingThreshold());
        saved.setDeliveryMinDays(incoming.getDeliveryMinDays()); saved.setDeliveryMaxDays(incoming.getDeliveryMaxDays());
        saved.setUpdatedAt(LocalDateTime.now()); return settingsRepo.save(saved);
    }
    @Transactional public StorePolicy publishPolicy(String key,StorePolicy input) {
        if(!KEYS.contains(key) || input.getTitle()==null || input.getTitle().isBlank()
                || input.getBody()==null || input.getBody().isBlank() || input.getBody().length()>10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nội dung chính sách không hợp lệ.");
        StorePolicy policy=new StorePolicy();
        policy.setVersion(policyRepo.findTopByPolicyKeyOrderByVersionDesc(key).map(p->p.getVersion()+1).orElse(1));
        policy.setPolicyKey(key); policy.setTitle(input.getTitle().trim()); policy.setBody(input.getBody().trim());
        policy.setEffectiveAt(input.getEffectiveAt()==null?LocalDateTime.now():input.getEffectiveAt());
        policy.setUpdatedAt(LocalDateTime.now()); policy.setApproved(true);
        return policyRepo.save(policy);
    }
    public Map<String,Object> publicSnapshot() {
        return Map.of("settings",settings(),"policies",activePolicies(),"paymentMethods",payments.availableMethods());
    }
    public Map<String,Object> adminSnapshot() {
        Map<String,Object> result=new LinkedHashMap<>(publicSnapshot());
        List<StorePolicy> versions=new ArrayList<>(policyRepo.findAll());
        versions.sort(Comparator.comparing(StorePolicy::getPolicyKey).thenComparing(StorePolicy::getVersion).reversed());
        result.put("history",versions);
        return result;
    }
    private String money(long value){return String.format(new Locale("vi","VN"),"%,dđ",value);}
}

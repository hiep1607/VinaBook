package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.fs.entity.StorePolicy;
import vn.fs.entity.StoreSettings;
import vn.fs.repository.StorePolicyRepository;
import vn.fs.repository.StoreSettingsRepository;

class StoreKnowledgeServiceTest {
    StoreSettingsRepository settingsRepo=mock(StoreSettingsRepository.class);
    StorePolicyRepository policyRepo=mock(StorePolicyRepository.class);
    PaymentGatewayService payments=mock(PaymentGatewayService.class);
    StoreKnowledgeService service=new StoreKnowledgeService(settingsRepo,policyRepo,payments);

    @Test void sharedShippingRuleUsesCurrentSettings() {
        StoreSettings settings=new StoreSettings();
        settings.setShippingFee(25000);settings.setFreeShippingThreshold(400000);
        when(settingsRepo.findById(1L)).thenReturn(Optional.of(settings));
        assertThat(service.shipping(399999)).isEqualTo(25000);
        assertThat(service.shipping(400000)).isZero();
        assertThat(service.text("shipping")).contains("25.000đ","400.000đ");
    }

    @Test void policyHistoryKeepsLatestEffectiveVersionAndFutureDraft() {
        StorePolicy old=policy(1,"Chính sách hiện tại",LocalDateTime.now().minusDays(10));
        StorePolicy newer=policy(2,"Chính sách mới",LocalDateTime.now().minusDays(1));
        StorePolicy future=policy(3,"Chính sách tương lai",LocalDateTime.now().plusDays(1));
        when(policyRepo.findAll()).thenReturn(List.of(old,newer,future));
        when(policyRepo.findTopByPolicyKeyOrderByVersionDesc("returns")).thenReturn(Optional.of(future));
        when(payments.availableMethods()).thenReturn(Map.of());

        assertThat(service.text("returns")).isEqualTo("Chính sách mới");
        StorePolicy input=new StorePolicy();input.setTitle("Bản cập nhật");input.setBody("Nội dung mới");
        service.publishPolicy("returns",input);
        verify(policyRepo).save(argThat(p -> p.getVersion()==4 && p.getId()==null
                && p.getBody().equals("Nội dung mới")));
    }

    private StorePolicy policy(int version,String body,LocalDateTime effective) {
        StorePolicy p=new StorePolicy();p.setPolicyKey("returns");p.setVersion(version);
        p.setTitle("Đổi trả");p.setBody(body);p.setEffectiveAt(effective);p.setApproved(true);
        return p;
    }
}

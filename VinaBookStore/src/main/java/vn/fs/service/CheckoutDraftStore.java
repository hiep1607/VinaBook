package vn.fs.service;

import javax.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import vn.fs.entity.CheckoutDraft;
import vn.fs.repository.*;

/** Private checkout data is independent of conversational history. */
@Service
public class CheckoutDraftStore {
    private final CheckoutDraftRepository drafts;
    private final UserRepository users;
    private final EntityManager em;
    private final ObjectMapper mapper=new ObjectMapper().configure(MapperFeature.USE_ANNOTATIONS,false)
        .setVisibility(PropertyAccessor.ALL,JsonAutoDetect.Visibility.NONE).setVisibility(PropertyAccessor.FIELD,JsonAutoDetect.Visibility.ANY);
    public CheckoutDraftStore(CheckoutDraftRepository drafts,UserRepository users,EntityManager em){this.drafts=drafts;this.users=users;this.em=em;}
    private void lockOwner(String email){em.lock(users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)),LockModeType.PESSIMISTIC_WRITE);}
    @Transactional public void replace(ChatCheckoutService.Draft draft) {
        lockOwner(draft.getOwner());drafts.deletePending(draft.getOwner());
        CheckoutDraft row=new CheckoutDraft();row.setToken(draft.getToken());row.setOwnerEmail(draft.getOwner());
        row.setExpiresAt(draft.getExpiresAt());row.setPayloadJson(encode(draft));drafts.saveAndFlush(row);
    }
    @Transactional(readOnly=true) public ChatCheckoutService.Draft get(String token,String email) {
        return decode(drafts.findByTokenAndOwnerEmail(token,email).orElseThrow(()->new ResponseStatusException(HttpStatus.GONE,"Bản nháp đã hết hạn hoặc được thay thế. Bạn xem lại đơn nhé.")));
    }
    @Transactional public ChatCheckoutService.Draft locked(String token,String email) {
        lockOwner(email);return decode(drafts.locked(token,email).orElseThrow(()->new ResponseStatusException(HttpStatus.GONE,"Bản nháp đã hết hạn hoặc được thay thế.")));
    }
    @Transactional public void confirmed(String token,Long orderId){var row=drafts.findById(token).orElseThrow();row.setOrderId(orderId);drafts.save(row);}
    @Transactional public void cancel(String token,String email) {
        lockOwner(email);var found=drafts.locked(token,email);
        if(found.isEmpty())return;
        if(found.get().getOrderId()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã được tạo. Hãy dùng thao tác hủy đơn hàng.");
        drafts.delete(found.get());
    }
    @Scheduled(fixedDelay=3600000,initialDelay=3600000) @Transactional
    public void expire(){drafts.deleteByExpiresAtLessThan(System.currentTimeMillis()-86400000L);}
    private ChatCheckoutService.Draft decode(CheckoutDraft row) {
        try {
            var draft=mapper.readValue(row.getPayloadJson(),ChatCheckoutService.Draft.class);
            draft.setOwner(row.getOwnerEmail());draft.setOrderId(row.getOrderId());return draft;
        }catch(java.io.IOException corrupt){throw new IllegalStateException("Cannot restore checkout draft",corrupt);}
    }
    private String encode(ChatCheckoutService.Draft draft){try{return mapper.writeValueAsString(draft);}catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}}
}

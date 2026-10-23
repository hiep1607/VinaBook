package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import vn.fs.entity.*;
import vn.fs.repository.*;

class CheckoutDraftStoreTest {
    @Test void recreationRestoresPrivateQuoteAndConfirmedOrderWithoutSharingOwners() {
        var rows=mock(CheckoutDraftRepository.class);var users=mock(UserRepository.class);
        var em=mock(EntityManager.class);var saved=new AtomicReference<CheckoutDraft>();
        var user=new User();user.setEmail("reader@example.invalid");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(rows.saveAndFlush(any())).thenAnswer(call->{saved.set(call.getArgument(0));return saved.get();});
        when(rows.findByTokenAndOwnerEmail(anyString(),anyString())).thenAnswer(call->Optional.ofNullable(saved.get())
            .filter(row->row.getToken().equals(call.getArgument(0)) && row.getOwnerEmail().equals(call.getArgument(1))));
        var draft=new ChatCheckoutService.Draft();draft.setOwner(user.getEmail());draft.setToken("private-quote-test");
        draft.setRecipient("Người nhận thử");draft.setPhone("0901234567");draft.setStreet("12 Đường Thử");
        draft.setFromCart(true);draft.setCartRevision(19);draft.setExpiresAt(System.currentTimeMillis()+600000);
        draft.setItems(List.of(new ChatCheckoutService.QuoteLine(57L,"Đắc Nhân Tâm",2,70520)));
        new CheckoutDraftStore(rows,users,em).replace(draft);
        saved.get().setOrderId(123L);
        var restored=new CheckoutDraftStore(rows,users,em).get(draft.getToken(),user.getEmail());
        assertThat(restored.getRecipient()).isEqualTo(draft.getRecipient());
        assertThat(restored.isFromCart()).isTrue();assertThat(restored.getCartRevision()).isEqualTo(19);
        assertThat(restored.getItems()).containsExactlyElementsOf(draft.getItems());assertThat(restored.getOrderId()).isEqualTo(123L);
        assertThatThrownBy(()->new CheckoutDraftStore(rows,users,em).get(draft.getToken(),"other@example.invalid"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}

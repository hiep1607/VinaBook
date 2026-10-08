package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class SupportConversationTest {
    CurrentUserAccess access=mock(CurrentUserAccess.class); SupportRequestRepository requests=mock(SupportRequestRepository.class);
    SupportMessageRepository messages=mock(SupportMessageRepository.class); EntityManager em=mock(EntityManager.class);
    CustomerSupportService service=new CustomerSupportService(access,requests,mock(OrderRepository.class),mock(ChatOrderSupportService.class),em);
    SupportRequest request=new SupportRequest(); CustomerSupportService.Message input=new CustomerSupportService.Message();
    @BeforeEach void setup(){
        service.setMessages(messages);when(access.currentUserId()).thenReturn(1L);request.setId(7L);request.setOwnerId(1L);
        when(requests.findById(7L)).thenReturn(Optional.of(request));when(em.find(SupportRequest.class,7L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(request);
        input.setClientKey("message-test-key");input.setContent("Xin kiểm tra giúp mình.");
        when(messages.saveAndFlush(any())).thenAnswer(call->{SupportMessage m=call.getArgument(0);m.setId(9L);return m;});
    }
    @Test void anotherOwnerCannotReadOrSend(){when(access.currentUserId()).thenReturn(2L);assertThatThrownBy(()->service.threadMessages(7L,0,0,false)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.sendMessage(7L,input,false)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(messages);}
    @Test void onlyAnAdminCanUseStaffRoute(){assertThatThrownBy(()->service.sendMessage(7L,input,true)).isInstanceOf(ResponseStatusException.class);when(access.isAdmin()).thenReturn(true);assertThat(service.sendMessage(7L,input,true)).containsEntry("staff",true);assertThat(request.getStatus()).isEqualTo("IN_REVIEW");}
    @Test void retryUsesSameMessageAndRejectsChangedContent(){var first=service.sendMessage(7L,input,false);var capture=org.mockito.ArgumentCaptor.forClass(SupportMessage.class);verify(messages).saveAndFlush(capture.capture());when(messages.findByRequestIdAndSenderIdAndClientKey(7L,1L,input.getClientKey())).thenReturn(Optional.of(capture.getValue()));assertThat(service.sendMessage(7L,input,false)).isEqualTo(first);verify(messages,times(1)).saveAndFlush(any());input.setContent("Nội dung khác");assertThatThrownBy(()->service.sendMessage(7L,input,false)).isInstanceOf(ResponseStatusException.class);}
    @Test void rejectsClosedOrInvalidMessages(){request.setStatus("RESOLVED");assertThatThrownBy(()->service.sendMessage(7L,input,false)).isInstanceOf(ResponseStatusException.class);request.setStatus("OPEN");input.setContent(" ");assertThatThrownBy(()->service.sendMessage(7L,input,false)).isInstanceOf(ResponseStatusException.class);input.setContent("x".repeat(2001));assertThatThrownBy(()->service.sendMessage(7L,input,false)).isInstanceOf(ResponseStatusException.class);verify(messages,never()).saveAndFlush(any());}
    @Test void responseOmitsPrivateIdentityAndInvalidCursorCannotQuery(){assertThat(service.sendMessage(7L,input,false)).doesNotContainKeys("senderId","email","token","clientKey");assertThatThrownBy(()->service.threadMessages(7L,1,2,false)).isInstanceOf(ResponseStatusException.class);verify(messages,never()).findTop50ByRequestIdOrderByIdDesc(any());}
}

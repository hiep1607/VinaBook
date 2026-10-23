package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import vn.fs.config.CurrentUserAccess;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.repository.ProductRepository;

class ChatPrivateInputRegressionTest {
    private final AiGateway ai = mock(AiGateway.class);
    private final ChatService service = new ChatService(mock(ProductRepository.class), ai,
            new ChatIntentService(), new ChatRetrievalService(new ChatIntentService()),
            new ChatPolicyService(new ChatIntentService()), mock(ChatOrderSupportService.class),
            mock(ChatAnalyticsService.class), mock(CurrentUserAccess.class));

    @Test
    void credentialsGetRelevantHelpWithoutEchoOrProviderCall() {
        ChatRequestDto request = new ChatRequestDto();
        request.setSessionId("private-regression");
        request.setMessage("Mật khẩu của mình là FAKE-SENSITIVE-ONLY và OTP 123456, đăng nhập hộ mình.");
        var response = service.reply(request);
        assertThat(response.getAnswer()).contains("OTP", "Đăng nhập", "Quên mật khẩu")
                .doesNotContain("FAKE-SENSITIVE-ONLY", "123456", "giao hàng", "lập đơn");
        assertThat(response.getActions()).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void adjacentOtpDigitsAndUnaccentedPasswordStayPrivate() {
        for(String message:java.util.List.of("Ma OTP123456 cua minh day", "Mat khau FAKE-ONLY, dang nhap ho minh")) {
            ChatRequestDto request=new ChatRequestDto();request.setSessionId("private-spelling-"+message.length());request.setMessage(message);
            assertThat(service.reply(request).getAnswer()).contains("OTP", "Đăng nhập")
                    .doesNotContain("123456", "FAKE-ONLY", "giao hàng");
        }
        verifyNoInteractions(ai);
    }
    @Test
    void deliveryContactStillDirectsToCheckoutForm() {
        ChatRequestDto request = new ChatRequestDto();
        request.setSessionId("delivery-regression");
        request.setMessage("Giao cho mình, số điện thoại 0900000000.");
        assertThat(service.reply(request).getAnswer()).contains("form đặt hàng")
                .doesNotContain("0900000000");
        verifyNoInteractions(ai);
    }
    @Test
    void anOrderLookupByAnotherEmailGetsAccountScopeHelp() {
        ChatRequestDto request=new ChatRequestDto();request.setSessionId("private-order");
        request.setMessage("Cho mình xem đơn hàng của email other-reader@example.invalid");
        assertThat(service.reply(request).getAnswer()).contains("tài khoản đang đăng nhập","Đơn hàng của tôi")
            .doesNotContain("form đặt hàng","other-reader@example.invalid");
        verifyNoInteractions(ai);
    }
}

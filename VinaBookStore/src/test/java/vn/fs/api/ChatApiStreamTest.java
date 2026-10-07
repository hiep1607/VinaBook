package vn.fs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.service.ChatAnalyticsService;
import vn.fs.service.ChatRateLimiter;
import vn.fs.service.ChatService;

class ChatApiStreamTest {
    private ChatService chat;
    private ChatRateLimiter limiter;
    private MockMvc mvc;

    @BeforeEach void setup() {
        chat = mock(ChatService.class);
        limiter = mock(ChatRateLimiter.class);
        when(limiter.allow(anyString())).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ChatApi(chat, limiter, mock(ChatAnalyticsService.class))).build();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private MvcResult start() throws Exception {
        return mvc.perform(post("/api/chat/stream").contentType("application/json")
            .content("{\"message\":\"Giỏ hàng của tôi\",\"sessionId\":\"chat-stream-test\"}"))
            .andExpect(request().asyncStarted()).andReturn();
    }

    @Test void streamsWithCallerAuthenticationAndApplicationClassLoader() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader applicationLoader = new ClassLoader(original) {};
        Authentication caller = new UsernamePasswordAuthenticationToken("test-account", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);
        AtomicReference<ClassLoader> observedLoader = new AtomicReference<>();
        AtomicReference<Authentication> observedCaller = new AtomicReference<>();
        when(chat.reply(any())).thenAnswer(invocation -> {
            observedLoader.set(Thread.currentThread().getContextClassLoader());
            observedCaller.set(SecurityContextHolder.getContext().getAuthentication());
            return new ChatResponseDto("Giỏ hàng đã sẵn sàng.", "local", "", false, List.of(), List.of());
        });
        try {
            Thread.currentThread().setContextClassLoader(applicationLoader);
            MvcResult pending = start();
            pending.getAsyncResult(5000);
            String stream = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(observedLoader.get()).isSameAs(applicationLoader);
            assertThat(observedCaller.get()).isSameAs(caller);
            assertThat(stream).contains("event:done", "Giỏ hàng đã sẵn sàng.");
        } finally { Thread.currentThread().setContextClassLoader(original); }
    }

    @Test void backendFailureFinishesStreamWithSafeErrorEvent() throws Exception {
        when(chat.reply(any())).thenThrow(new IllegalArgumentException("private-account-details"));
        MvcResult pending = start();
        pending.getAsyncResult(5000);
        String stream = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(stream).contains("event:error", "Bạn thử gửi lại câu hỏi").doesNotContain("private-account-details", "event:done");
    }

    @Test void rateLimitEndsStreamWithoutCallingChat() throws Exception {
        when(limiter.allow(anyString())).thenReturn(false);
        MvcResult pending = start();
        pending.getAsyncResult(5000);
        String stream = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(stream).contains("event:error", "một phút");
        verifyNoInteractions(chat);
    }
}

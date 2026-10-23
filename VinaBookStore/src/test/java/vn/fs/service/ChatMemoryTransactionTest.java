package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.chat.*;
import vn.fs.entity.ChatConversation;
import vn.fs.repository.*;
import vn.fs.config.CurrentUserAccess;

class ChatMemoryTransactionTest {
    static class Transactions extends AbstractPlatformTransactionManager {
        final AtomicReference<ChatConversation> stored;ChatConversation before;int rollbacks;
        Transactions(AtomicReference<ChatConversation> stored){this.stored=stored;}
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object tx,TransactionDefinition definition){before=stored.get();}
        protected void doCommit(DefaultTransactionStatus status){}
        protected void doRollback(DefaultTransactionStatus status){stored.set(before);rollbacks++;}
    }
    @Test void snapshotRollsBackWhenTranscriptWriteFailsThroughSpringProxy() {
        var conversations=mock(ChatConversationRepository.class);var exchanges=mock(ChatExchangeRepository.class);
        var stored=new AtomicReference<ChatConversation>();
        when(conversations.findById(anyString())).thenReturn(Optional.empty());
        when(conversations.save(any())).thenAnswer(call->{stored.set(call.getArgument(0));return stored.get();});
        when(exchanges.save(any())).thenThrow(new IllegalStateException("transcript unavailable"));
        var transactions=new Transactions(stored);
        var factory=new ProxyFactory(new ChatMemoryStore(conversations,exchanges,new ObjectMapper()));
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        var store=(ChatMemoryStore)factory.getProxy();
        var response=new ChatResponseDto("Đã chọn sách","local","",false,List.of(),List.of());
        assertThatThrownBy(()->store.save(7L,"chat-test-transaction-0001","request-1","Tìm sách",response,new ChatDialogueMemory.State(),true)).isInstanceOf(IllegalStateException.class);
        assertThat(stored.get()).isNull();assertThat(transactions.rollbacks).isEqualTo(1);
    }
    @Test void clearWhileProviderIsRunningCannotResurrectConversation() throws Exception {
        var language=new ChatIntentService();var products=mock(ProductRepository.class);var ai=mock(AiGateway.class);
        when(ai.isConfigured()).thenReturn(true);
        var access=mock(CurrentUserAccess.class);var memory=mock(ChatMemoryStore.class);
        var chat=new ChatService(products,ai,language,new ChatRetrievalService(language),new ChatPolicyService(language),mock(ChatOrderSupportService.class),mock(ChatAnalyticsService.class),access);
        var pipeline=mock(ChatStructuredPipeline.class);chat.setStructuredPipeline(pipeline);chat.setMemoryStore(memory);
        var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
        when(pipeline.reply(any(),any())).thenAnswer(call->{started.countDown();if(!finish.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test wait");return new ChatResponseDto("Sách đã chọn","local","",false,List.of(),List.of());});
        String session="chat-0123456789abcdef0123456789abcdef0123456789abcdef";
        var request=new ChatRequestDto();request.setMessage("Gợi ý tiểu thuyết");request.setSessionId(session);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var running=executor.submit(()->chat.reply(request));assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();
            chat.clearConversation(session);finish.countDown();
            assertThatThrownBy(()->running.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            verify(memory,never()).save(any(),any(),any(),any(),any(),any(),anyBoolean());
            verify(memory).clear(0L,session);
        }finally{finish.countDown();executor.shutdownNow();}
    }
}

package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.config.AiSettings;

class BookEmbeddingConcurrencyTest {
    @Test void unrelatedQueriesCanRunWithoutWaitingForOneGlobalNetworkLock() throws Exception {
        var started=new CountDownLatch(2);var finish=new CountDownLatch(1);var calls=new AtomicInteger();
        var gateway=new BookEmbeddingGateway(new AiSettings(),new ObjectMapper()){
            @Override protected List<Double> embed(String text,String type,String title){
                calls.incrementAndGet();started.countDown();
                try{if(!finish.await(3,TimeUnit.SECONDS))throw new IllegalStateException("test wait");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return List.of(1.0,0.0);
            }
        };
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->gateway.query("chủ đề A"));var b=executor.submit(()->gateway.query("chủ đề B"));
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();finish.countDown();
            assertThat(a.get(2,TimeUnit.SECONDS)).hasSize(2);assertThat(b.get(2,TimeUnit.SECONDS)).hasSize(2);
            gateway.query("chủ đề A");assertThat(calls.get()).isEqualTo(2);
        }finally{finish.countDown();executor.shutdownNow();}
    }
    @Test void exhaustedTurnBudgetSkipsRemoteEmbedding() {
        var gateway=new BookEmbeddingGateway(new AiSettings(),new ObjectMapper());var ai=mock(AiGateway.class);
        when(ai.remainingTurnMillis()).thenReturn(0L);gateway.setAi(ai);
        assertThatThrownBy(()->gateway.query("chủ đề mới")).hasMessageContaining("deadline");
    }
}

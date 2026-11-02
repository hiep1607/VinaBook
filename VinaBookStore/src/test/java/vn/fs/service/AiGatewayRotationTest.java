package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import vn.fs.config.AiSettings;

class AiGatewayRotationTest {
    static final ObjectMapper MAPPER=new ObjectMapper();
    static final String OK="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";
    static final String BUSY="{\"error\":{\"message\":\"overloaded\"}}";

    @Test void rotatesProactivelyAcrossTurnsAndAcrossAiStepsWithoutAnyErrors() throws Exception {
        try(Fixture f=new Fixture(exchange->reply(exchange,200,OK))) {
            AiGateway gateway=f.gateway();
            for(int turn=0;turn<3;turn++) {
                gateway.beginTurnBudget(10);
                try {
                    assertThat(gateway.completeStructured("system",List.of(),"understanding",MAPPER.createObjectNode())).isEqualTo("OK");
                    assertThat(gateway.complete("system",List.of(),"narration")).isEqualTo("OK");
                } finally {gateway.endTurnBudget();}
            }
            assertThat(f.keys).containsExactly("first","second","third","first","second","third");
            assertThat(gateway.modelUsed()).isEqualTo("test-model");
        }
    }

    @Test void afterFailoverNextTurnStartsAfterTheKeyThatActuallyAnswered() throws Exception {
        AtomicInteger firstCalls=new AtomicInteger();
        try(Fixture f=new Fixture(exchange->{
            boolean busy="first".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key")) && firstCalls.incrementAndGet()==1;
            reply(exchange,busy?503:200,busy?BUSY:OK);
        })) {
            AiGateway gateway=f.gateway();
            for(int turn=0;turn<4;turn++) {
                gateway.beginTurnBudget(10);
                try {assertThat(gateway.completeStructured("system",List.of(),"question",MAPPER.createObjectNode())).isEqualTo("OK");}
                finally {gateway.endTurnBudget();}
            }
            assertThat(f.keys).containsExactly("first","second","third","first","second");
        }
    }

    @Test void skippingAQuotaLimitedKeyDoesNotReuseTheSuccessfulBackupForTheNextTurn() throws Exception {
        try(Fixture f=new Fixture(exchange->{
            boolean limited="first".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            reply(exchange,limited?429:200,limited?"{\"error\":{\"message\":\"requests per day quota\"}}":OK);
        })) {
            AiGateway gateway=f.gateway();
            for(int turn=0;turn<3;turn++) {
                gateway.beginTurnBudget(10);
                try {assertThat(gateway.complete("system",List.of(),"question")).isEqualTo("OK");}
                finally {gateway.endTurnBudget();}
            }
            assertThat(f.keys).containsExactly("first","second","third","second");
        }
    }

    @Test void delayedFailoverDoesNotRewindKeysReservedByOtherConversations() throws Exception {
        CountDownLatch firstEntered=new CountDownLatch(1),releaseFirst=new CountDownLatch(1);
        AtomicInteger firstCalls=new AtomicInteger();
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try(Fixture f=new Fixture(exchange->{
            boolean delayed="first".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key")) && firstCalls.incrementAndGet()==1;
            if(delayed) {
                firstEntered.countDown();
                try {releaseFirst.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
            reply(exchange,delayed?503:200,delayed?BUSY:OK);
        })) {
            AiGateway gateway=f.gateway();
            Future<String> delayed=caller.submit(()->gateway.complete("system",List.of(),"conversation A"));
            assertThat(firstEntered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(gateway.complete("system",List.of(),"conversation B")).isEqualTo("OK");
            assertThat(gateway.complete("system",List.of(),"conversation C")).isEqualTo("OK");
            releaseFirst.countDown();
            assertThat(delayed.get(5,TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(gateway.complete("system",List.of(),"conversation D")).isEqualTo("OK");
            assertThat(f.keys).containsExactly("first","second","third","second","first");
        } finally {releaseFirst.countDown();caller.shutdownNow();}
    }

    private static void reply(HttpExchange exchange,int status,String body) {
        try {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        }catch(java.io.IOException ignored){/* Cancelled request. */}
        finally {exchange.close();}
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final ExecutorService workers=Executors.newCachedThreadPool();
        final List<String> keys=new CopyOnWriteArrayList<>();
        Fixture(HttpHandler handler)throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);
            server.createContext("/models/test-model:generateContent",exchange->{keys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));handler.handle(exchange);});
            server.start();
        }
        AiGateway gateway() {
            AiSettings settings=new AiSettings();settings.setProvider("gemini");settings.setModel("test-model");
            settings.setGeminiApiKey1("first");settings.setGeminiApiKey2("second");settings.setGeminiApiKey3("third");
            settings.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            return new AiGateway(settings,MAPPER);
        }
        @Override public void close(){server.stop(0);workers.shutdownNow();}
    }
}

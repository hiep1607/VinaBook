package vn.fs.service;

import static org.assertj.core.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import vn.fs.config.AiSettings;

class AiGatewayResilienceTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";
    static final String BUSY = "{\"error\":{\"message\":\"overloaded\"}}";

    @Test void stalledFirstKeyLeavesTimeForAnotherKeyWithinTheSameStage() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        try (Fixture f = new Fixture(exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            seen.add(key);
            if ("first".equals(key)) pause(2200);
            reply(exchange,200,OK);
        })) {
            AiSettings settings = f.settings();settings.setGeminiAttemptTimeoutMillis(350);
            settings.setGeminiApiKey2("second");
            AiGateway gateway = new AiGateway(settings,MAPPER);
            gateway.beginTurnBudget(3);gateway.beginStageBudget(3);
            long start = System.nanoTime();
            try {
                assertThat(gateway.completeStructured("system",List.of(),"question",MAPPER.createObjectNode())).isEqualTo("OK");
                assertThat((System.nanoTime()-start)/1000000).isLessThan(1800);
                assertThat(seen).containsExactly("first","second");
            } finally {gateway.endTurnBudget();}
        }
    }

    @Test void fourKeysDoNotMultiplyAnOptionalCallIntoTwelveHttpRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (Fixture f = new Fixture(exchange -> {requests.incrementAndGet();reply(exchange,503,BUSY);})) {
            AiSettings settings=f.settings();settings.setGeminiApiKey2("second");
            settings.setGeminiApiKey3("third");settings.setGeminiApiKey4("fourth");
            AiGateway gateway=new AiGateway(settings,MAPPER);
            assertThatThrownBy(()->gateway.complete("system",List.of(),"question")).hasMessageContaining("503");
            assertThat(requests.get()).isEqualTo(4);
        }
    }

    @Test void temporary503DoesNotDisableEveryKeyForFifteenSeconds() throws Exception {
        AtomicBoolean busy=new AtomicBoolean(true);
        AtomicInteger requests=new AtomicInteger();
        try (Fixture f = new Fixture(exchange->{requests.incrementAndGet();reply(exchange,busy.get()?503:200,busy.get()?BUSY:OK);})) {
            AiSettings settings=f.settings();settings.setGeminiApiKey2("second");
            AiGateway gateway=new AiGateway(settings,MAPPER);
            assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"question",MAPPER.createObjectNode())).hasMessageContaining("503");
            int failedCalls=requests.get();
            busy.set(false);
            assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"too soon",MAPPER.createObjectNode())).isInstanceOf(IllegalStateException.class);
            assertThat(requests.get()).isEqualTo(failedCalls);
            Thread.sleep(2200);
            assertThat(gateway.completeStructured("system",List.of(),"recovered",MAPPER.createObjectNode())).isEqualTo("OK");
            assertThat(gateway.modelUsed()).isEqualTo("test-model");
        }
    }

    @Test void rateLimitUsesRetryAfterInsteadOfAlwaysDisablingTheKeyForOneMinute() throws Exception {
        assertRateLimitRecovers(false);
    }

    @Test void rateLimitAlsoHonoursGoogleRetryInfoWithoutAHeader() throws Exception {
        assertRateLimitRecovers(true);
    }

    private void assertRateLimitRecovers(boolean retryInfo) throws Exception {
        AtomicInteger requests=new AtomicInteger();
        try (Fixture f = new Fixture(exchange->{
            int count=requests.incrementAndGet();
            if(count==1) {
                if(!retryInfo)exchange.getResponseHeaders().set("Retry-After","1");
                reply(exchange,429,retryInfo?"{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"1s\"}]}}":"{\"error\":{\"message\":\"rate limit\"}}");
            } else reply(exchange,200,OK);
        })) {
            AiGateway gateway=new AiGateway(f.settings(),MAPPER);
            assertThatThrownBy(()->gateway.complete("system",List.of(),"limited")).hasMessageContaining("429");
            assertThatThrownBy(()->gateway.complete("system",List.of(),"too soon")).isInstanceOf(IllegalStateException.class);
            assertThat(requests.get()).isEqualTo(1);
            Thread.sleep(1200);
            assertThat(gateway.complete("system",List.of(),"recovered")).isEqualTo("OK");
            assertThat(requests.get()).isEqualTo(2);
        }
    }

    @Test void cancellingATurnDoesNotQuarantineHealthyKeys() throws Exception {
        AtomicInteger requests=new AtomicInteger();
        try(Fixture f=new Fixture(exchange->{requests.incrementAndGet();reply(exchange,200,OK);})) {
            AiSettings settings=f.settings();settings.setGeminiApiKey2("second");
            AiGateway gateway=new AiGateway(settings,MAPPER);
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"cancelled",MAPPER.createObjectNode()))
                    .isInstanceOf(IllegalStateException.class);
            } finally {Thread.interrupted();}
            assertThat(requests.get()).isZero();
            assertThat(gateway.completeStructured("system",List.of(),"fresh turn",MAPPER.createObjectNode())).isEqualTo("OK");
            assertThat(requests.get()).isEqualTo(1);
        }
    }

    @Test void shortStageFailureDoesNotQuarantineAKeyForTheNextStage() throws Exception {
        AtomicInteger requests=new AtomicInteger();
        try(Fixture f=new Fixture(exchange->{if(requests.incrementAndGet()<=2)pause(2200);reply(exchange,200,OK);})) {
            AiSettings settings=f.settings();settings.setGeminiAttemptTimeoutMillis(300);
            AiGateway gateway=new AiGateway(settings,MAPPER);gateway.beginTurnBudget(5);gateway.beginStageBudget(1);
            try {
                assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"slow",MAPPER.createObjectNode())).isInstanceOf(IllegalStateException.class);
                gateway.endStageBudget();
                assertThat(gateway.completeStructured("system",List.of(),"next stage",MAPPER.createObjectNode())).isEqualTo("OK");
                assertThat(requests.get()).isEqualTo(3);
            } finally {gateway.endTurnBudget();}
        }
    }

    private static void pause(long millis) {
        try {Thread.sleep(millis);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
    private static void reply(HttpExchange exchange,int status,String body) {
        try {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var stream=exchange.getResponseBody()){stream.write(bytes);}
        }catch(java.io.IOException closed){/* Caller may have cancelled a slow attempt. */}
        finally {exchange.close();}
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService workers=Executors.newCachedThreadPool();
        Fixture(HttpHandler handler)throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(workers);server.createContext("/models/test-model:generateContent",handler);server.start();
        }
        AiSettings settings() {
            AiSettings settings=new AiSettings();settings.setProvider("gemini");settings.setModel("test-model");
            settings.setGeminiApiKey1("first");settings.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            return settings;
        }
        @Override public void close(){server.stop(0);workers.shutdownNow();}
    }
}

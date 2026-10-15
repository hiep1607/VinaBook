package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import vn.fs.config.AiSettings;

class AiGatewayStructuredTest {
    ObjectMapper mapper=new ObjectMapper();
    @Test void sendsSchemaToGeminiAndDoesNotLeakItToNextOrdinaryCall() throws Exception {
        List<JsonNode> bodies=new CopyOnWriteArrayList<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/models/test:generateContent",exchange->{
            bodies.add(mapper.readTree(exchange.getRequestBody()));
            byte[] response="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            try(var output=exchange.getResponseBody()){output.write(response);}
        });server.start();
        try {
            AiGateway gateway=gateway(server);
            JsonNode schema=mapper.readTree("{\"type\":\"object\",\"properties\":{\"intent\":{\"type\":\"string\"}}}");
            gateway.completeStructured("system",List.of(),"question",schema);
            gateway.complete("system",List.of(),"next");
            assertThat(bodies.get(0).path("generationConfig").path("responseJsonSchema")).isEqualTo(schema);
            assertThat(bodies.get(1).path("generationConfig").has("responseJsonSchema")).isFalse();
        } finally {server.stop(0);}
    }
    @Test void turnDeadlineEndsProcessingAndCanBeClearedForFollowingTurn() {
        var settings=new AiSettings();settings.setProvider("gemini");settings.setModel("test");settings.setApiKey("fake-test-key");
        AiGateway gateway=new AiGateway(settings,mapper);gateway.beginTurnBudget(0);
        assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"question",mapper.createObjectNode()))
            .hasMessageContaining("deadline");
        gateway.endTurnBudget();
    }
    @Test void structuredRequestDoesNotMultiplyRetriesForEachKey() throws Exception {
        List<String> requests=new CopyOnWriteArrayList<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/models/test:generateContent",exchange->{
            requests.add(exchange.getRequestURI().getPath());
            byte[] response="{\"error\":{\"message\":\"unavailable\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503,response.length);
            try(var output=exchange.getResponseBody()){output.write(response);}
        });server.start();
        try {
            AiGateway gateway=gateway(server);
            assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"question",mapper.createObjectNode())).isInstanceOf(IllegalStateException.class);
            assertThat(requests).hasSize(1);
        } finally {server.stop(0);}
    }
    @Test void optionalStageTimeoutDoesNotConsumeTheNextStageBudget() throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        var workers=java.util.concurrent.Executors.newCachedThreadPool();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(workers);
        server.createContext("/models/test:generateContent",exchange->{
            try {
                if(requests.incrementAndGet()==1)Thread.sleep(3000);
                byte[] response="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,response.length);
                try(var output=exchange.getResponseBody()){output.write(response);}
            } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();}
            finally {exchange.close();}
        });server.start();
        try {
            AiGateway gateway=gateway(server);gateway.beginTurnBudget(15);gateway.beginStageBudget(1);
            long started=System.nanoTime();
            assertThatThrownBy(()->gateway.completeStructured("system",List.of(),"slow",mapper.createObjectNode()))
                .isInstanceOf(IllegalStateException.class);
            assertThat((System.nanoTime()-started)/1_000_000L).isLessThan(2500);
            gateway.endStageBudget();
            assertThat(gateway.completeStructured("system",List.of(),"next",mapper.createObjectNode())).isEqualTo("{}");
            gateway.endTurnBudget();assertThat(requests.get()).isEqualTo(2);
        } finally {server.stop(0);workers.shutdownNow();}
    }
    private AiGateway gateway(HttpServer server) {
        var settings=new AiSettings();settings.setProvider("gemini");settings.setModel("test");settings.setApiKey("fake-test-key");
        settings.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        return new AiGateway(settings,mapper);
    }
}

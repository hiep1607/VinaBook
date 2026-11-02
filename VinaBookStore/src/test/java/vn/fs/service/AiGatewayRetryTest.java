package vn.fs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import vn.fs.config.AiSettings;

class AiGatewayRetryTest {
    @Test
    void bindsFourNamedKeySlotsFromSpringProperties() {
        AiSettings settings = new Binder(new MapConfigurationPropertySource(Map.of(
                "ai.gemini-api-key1", "first", "ai.gemini-api-key2", "second",
                "ai.gemini-api-key3", "third", "ai.gemini-api-key4", "fourth",
                "ai.gemini-attempt-timeout-millis", "2500")))
                .bind("ai", Bindable.of(AiSettings.class)).get();
        assertThat(settings.getGeminiApiKeys()).containsExactly("first", "second", "third", "fourth");
        assertThat(settings.getGeminiAttemptTimeoutMillis()).isEqualTo(2500);
    }

    @Test
    void retriesTemporary503AndReturnsTheNextSuccessfulAnswer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            int number = calls.incrementAndGet();
            int status = number == 1 ? 503 : 200;
            byte[] body = (number == 1 ? "{\"error\":{\"message\":\"overloaded\"}}"
                    : "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"internal\"},{\"text\":\"OK\"}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("OK");
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesBackupModelAfterPrimary503WithoutCoolingDownTheKeyForBackup() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path);
            boolean backup = path.contains("backup-model");
            byte[] body = (backup ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"backup answer\"}]}}]}"
                    : "{\"error\":{\"message\":\"overloaded\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(backup ? 200 : 503, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setFallbackModel("backup-model");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("backup answer");
            assertThat(gateway.modelUsed()).isEqualTo("backup-model");
            assertThat(requests).containsExactly(
                    "/models/test-model:generateContent", "/models/backup-model:generateContent");
            assertThat(gateway.complete("system", List.of(), "next question")).isEqualTo("backup answer");
            assertThat(requests).hasSize(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void quotaOnAllPrimaryKeysCanUseTheSameKeysForBackupModel() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            requests.add(path + ":" + key);
            boolean backup = path.contains("backup-model");
            byte[] body = (backup ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}"
                    : "{\"error\":{\"message\":\"quota\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(backup ? 200 : 429, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setGeminiApiKey1("first");
            settings.setGeminiApiKey2("second");
            settings.setFallbackModel("backup-model");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("OK");
            assertThat(requests).containsExactly(
                    "/models/test-model:generateContent:first",
                    "/models/test-model:generateContent:second",
                    "/models/backup-model:generateContent:second");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryQuotaErrors() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"quota\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiGateway gateway = new AiGateway(settings(server.getAddress().getPort()), new ObjectMapper());
            assertThatThrownBy(() -> gateway.complete("system", List.of(), "question"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("429");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rotatesAcrossFourNumberedKeysOnDailyQuotaWithoutChangingModel() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            seen.add(key);
            int status = "fourth".equals(key) ? 200 : 429;
            byte[] body = (status == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"backup works\"}]}}]}"
                    : "{\"error\":{\"message\":\"quota\",\"details\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setApiKey("legacy-key");
            settings.setGeminiApiKey1("primary");
            settings.setGeminiApiKey2("secondary");
            settings.setGeminiApiKey3("third");
            settings.setGeminiApiKey4("fourth");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "first")).isEqualTo("backup works");
            assertThat(gateway.complete("system", List.of(), "second")).isEqualTo("backup works");
            assertThat(seen).containsExactly("primary", "secondary", "third", "fourth", "fourth");
            assertThat(gateway.modelUsed()).isEqualTo("test-model");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void worksWithNumberedKeysOnlyWithoutRepeating503OnTheSameKey() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            seen.add(key);
            int status = "first".equals(key) ? 503 : 200;
            byte[] body = (status == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}"
                    : "{\"error\":{\"message\":\"overloaded\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setApiKey("");
            settings.setGeminiApiKey1("first");
            settings.setGeminiApiKey2("second");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.isConfigured()).isTrue();
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("OK");
            assertThat(seen).containsExactly("first", "second");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotSendBadRequestToAnotherKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"invalid prompt\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setGeminiApiKey1("test-key");
            settings.setGeminiApiKey2("backup");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThatThrownBy(() -> gateway.complete("system", List.of(), "question"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("400");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void switchesKeyWhenGoogleMarksItInvalidWith400() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            seen.add(key);
            int status = "invalid".equals(key) ? 400 : 200;
            byte[] body = (status == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}"
                    : "{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setGeminiApiKey1("invalid");
            settings.setGeminiApiKey2("valid");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("OK");
            assertThat(seen).containsExactly("invalid", "valid");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void switchesKeyWhenHttp200ContainsOnlyThoughts() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            seen.add(key);
            byte[] body = ("first".equals(key)
                    ? "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"}]}}]}"
                    : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"usable answer\"}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setGeminiApiKey1("first");
            settings.setGeminiApiKey2("second");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("usable answer");
            assertThat(seen).containsExactly("first", "second");
            assertThat(gateway.complete("system", List.of(), "another question")).isEqualTo("usable answer");
            assertThat(seen).containsExactly("first", "second", "second");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void distributesRequestsAcrossHealthyKeys() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test-model:generateContent", exchange -> {
            seen.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            AiSettings settings = settings(server.getAddress().getPort());
            settings.setGeminiApiKey1("first");
            settings.setGeminiApiKey2("second");
            settings.setGeminiApiKey3("third");
            AiGateway gateway = new AiGateway(settings, new ObjectMapper());
            for (int i = 0; i < 4; i++)
                assertThat(gateway.complete("system", List.of(), "question")).isEqualTo("OK");
            assertThat(seen).containsExactly("first", "second", "third", "first");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsOnlyFourthKeySlotAndIgnoresEmptyOrPlaceholderSlots() {
        AiSettings settings = new AiSettings();
        settings.setProvider("gemini");
        settings.setModel("test-model");
        settings.setGeminiApiKey1("PASTE_GEMINI_API_KEY_HERE");
        settings.setGeminiApiKey2(" ");
        settings.setGeminiApiKey4(" fourth ");
        AiGateway gateway = new AiGateway(settings, new ObjectMapper());
        assertThat(gateway.isConfigured()).isTrue();
        assertThat(settings.getGeminiApiKeys()).containsExactly("fourth");
        settings.setGeminiApiKey3("fourth");
        assertThat(settings.getGeminiApiKeys()).containsExactly("fourth");
    }

    private AiSettings settings(int port) {
        AiSettings settings = new AiSettings();
        settings.setProvider("gemini");
        settings.setModel("test-model");
        settings.setApiKey("test-key");
        settings.setBaseUrl("http://127.0.0.1:" + port);
        return settings;
    }
}

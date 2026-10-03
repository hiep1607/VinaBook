package vn.fs.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.config.AiSettings;

/** Embeddings are prepared for approved book text, never for prices or customer data. */
@Service
public class BookEmbeddingGateway {
    private final AiSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String,CachedQuery> queryCache=new LinkedHashMap<>(32,0.75f,true);
    private volatile long retryAfter;
    private record CachedQuery(List<Double> vector,long expiresAt) {}

    public BookEmbeddingGateway(AiSettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    public boolean available() {
        return "gemini".equalsIgnoreCase(settings.getProvider())
                && !settings.getGeminiApiKeys().isEmpty();
    }

    public List<Double> document(String title, String text) { return embed(text, "RETRIEVAL_DOCUMENT", title); }
    public record Document(String title,String text) {}
    public List<List<Double>> documents(List<Document> documents) {
        if(documents.isEmpty())return List.of();
        if(documents.size()>100)throw new IllegalArgumentException("Embedding batch too large");
        List<Map<String,Object>> requests=documents.stream().map(document->body(document.text(),"RETRIEVAL_DOCUMENT",document.title())).toList();
        JsonNode embeddings=post("batchEmbedContents",Map.of("requests",requests)).path("embeddings");
        if(!embeddings.isArray() || embeddings.size()!=documents.size())throw new IllegalStateException("Chỉ mục theo lô thiếu dữ liệu");
        List<List<Double>> result=new ArrayList<>();
        embeddings.forEach(embedding->result.add(vector(embedding.path("values"))));return result;
    }
    public synchronized List<Double> query(String text) {
        String normalized=text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        String key=settings.getEmbeddingModel()+":"+normalized;
        long now=System.currentTimeMillis();
        CachedQuery cached=queryCache.get(key);
        if(cached!=null && cached.expiresAt()>now)return cached.vector();
        List<Double> vector=List.copyOf(embed(normalized,"RETRIEVAL_QUERY",null));
        queryCache.put(key,new CachedQuery(vector,now+900000));
        while(queryCache.size()>256)queryCache.remove(queryCache.keySet().iterator().next());
        return vector;
    }

    protected List<Double> embed(String text, String taskType, String title) {
        return vector(post("embedContent",body(text,taskType,title)).path("embedding").path("values"));
    }
    private Map<String,Object> body(String text,String taskType,String title) {
        Map<String,Object> config=title==null?Map.of("taskType",taskType,"outputDimensionality",768):
            Map.of("taskType",taskType,"title",title,"outputDimensionality",768);
        return Map.of("model","models/"+settings.getEmbeddingModel(),"content",Map.of("parts",List.of(Map.of("text",text))),"embedContentConfig",config);
    }
    private JsonNode post(String operation,Map<String,Object> body) {
        if (!available()) throw new IllegalStateException("Gemini embeddings chưa được cấu hình");
        if(System.currentTimeMillis()<retryAfter)throw new IllegalStateException("Chỉ mục ngữ nghĩa đang chờ dịch vụ phục hồi");
        String model = settings.getEmbeddingModel();
        if (model == null || !model.matches("[A-Za-z0-9._-]{1,80}"))
            throw new IllegalStateException("Tên embedding model không hợp lệ");
        try {
            String payload = mapper.writeValueAsString(body);
            HttpResponse<String> response = null;
            int lastStatus = 0;
            for (String key : settings.getGeminiApiKeys()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":"+operation))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", key)
                        .timeout(Duration.ofSeconds(Math.max(3, settings.getTimeoutSeconds())))
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                lastStatus = response.statusCode();
                if (lastStatus >= 200 && lastStatus < 300) break;
                boolean invalidKey = lastStatus == 400 && response.body().contains("\"API_KEY_INVALID\"");
                if (!invalidKey && lastStatus != 401 && lastStatus != 402 && lastStatus != 403 && lastStatus != 408 && lastStatus != 429
                        && lastStatus != 500 && lastStatus != 502 && lastStatus != 503 && lastStatus != 504)
                    throw new IllegalStateException("Dịch vụ tạo chỉ mục ngữ nghĩa trả mã " + lastStatus);
            }
            if (response == null || lastStatus < 200 || lastStatus >= 300) {
                retryAfter=System.currentTimeMillis()+60000;
                throw new IllegalStateException("Dịch vụ tạo chỉ mục ngữ nghĩa trả mã " + lastStatus);
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tạo chỉ mục ngữ nghĩa bị gián đoạn", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            retryAfter=System.currentTimeMillis()+60000;
            throw new IllegalStateException("Không tạo được chỉ mục ngữ nghĩa", ex);
        }
    }
    private List<Double> vector(JsonNode values) {
        if(!values.isArray() || values.isEmpty())throw new IllegalStateException("Chỉ mục ngữ nghĩa trống");
        List<Double> result=new ArrayList<>();
        for(JsonNode value:values) {
            if(!value.isNumber() || !Double.isFinite(value.asDouble()))throw new IllegalStateException("Chỉ mục ngữ nghĩa không hợp lệ");
            result.add(value.asDouble());
        }
        return result;
    }
}

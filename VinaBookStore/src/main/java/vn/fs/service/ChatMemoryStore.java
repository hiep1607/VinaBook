package vn.fs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.entity.ChatConversation;
import vn.fs.entity.ChatExchange;
import vn.fs.repository.ChatConversationRepository;
import vn.fs.repository.ChatExchangeRepository;

/** Durable history belongs to a single authenticated user or to an unguessable guest session. */
@Service
public class ChatMemoryStore {
    private final ChatConversationRepository conversations;
    private final ChatExchangeRepository exchanges;
    private final ObjectMapper mapper;

    public ChatMemoryStore(ChatConversationRepository conversations, ChatExchangeRepository exchanges, ObjectMapper mapper) {
        this.conversations = conversations;
        this.exchanges = exchanges;
        this.mapper = mapper;
    }

    static String key(Long userId, String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{16,64}")) return null;
        if (userId == null && !sessionId.matches("chat-[0-9a-f]{48}")) return null;
        return (userId == null ? "g:" : "u:" + userId + ":") + sessionId;
    }

    @Transactional(readOnly = true)
    void restore(Long userId, String sessionId, ChatDialogueMemory.State state) {
        String key = key(userId, sessionId);
        if (key != null) conversations.findById(key).ifPresent(saved -> {
            if (saved.getStateJson() == null || saved.getStateJson().isBlank()) return;
            try {
                mapper.readerForUpdating(state).readValue(saved.getStateJson());
            } catch (Exception ex) {
                // A damaged snapshot must not make the saved transcript inaccessible.
                state.initialized = false;
            }
        });
        // Upgrade old snapshots that only remembered the latest discovery list.
        if(key!=null && state.recommendationHistoryVersion<1) {
            for(ChatExchange exchange:exchanges.findByConversationIdOrderByIdAsc(key)) {
                ChatResponseDto response=readResponse(exchange.getResponseJson());
                if(response!=null && response.getProducts()!=null)
                    response.getProducts().forEach(book->state.shownProductIds.add(book.getId()));
            }
            state.recommendationHistoryVersion=1;
        }
        state.touched = System.currentTimeMillis();
        state.loaded = true;
    }

    @Transactional(readOnly = true)
    ChatResponseDto duplicate(Long userId, String sessionId, String requestId) {
        String key = key(userId, sessionId);
        if (key == null || requestId == null || requestId.isBlank()) return null;
        return exchanges.findByConversationIdAndRequestId(key, requestId)
                .map(row -> readResponse(row.getResponseJson())).orElse(null);
    }

    @Transactional
    void save(Long userId, String sessionId, String requestId, String question,
            ChatResponseDto response, ChatDialogueMemory.State state, boolean saveTranscript) {
        String key = key(userId, sessionId);
        if (key == null) return;
        try {
            ChatConversation conversation = conversations.findById(key).orElseGet(ChatConversation::new);
            if (conversation.getId() == null) {
                conversation.setId(key);
                conversation.setUserId(userId);
                conversation.setSessionId(sessionId);
                conversation.setCreatedAt(LocalDateTime.now());
            }
            conversation.setStateJson(mapper.writeValueAsString(state));
            conversation.setUpdatedAt(LocalDateTime.now());
            conversations.save(conversation);
            if (saveTranscript && requestId != null && !requestId.isBlank()
                    && exchanges.findByConversationIdAndRequestId(key, requestId).isEmpty()) {
                ChatExchange row = new ChatExchange();
                row.setConversationId(key);
                row.setRequestId(requestId);
                row.setUserText(question);
                row.setResponseJson(mapper.writeValueAsString(response));
                row.setCreatedAt(LocalDateTime.now());
                exchanges.save(row);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot persist chat context", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(Long userId, String sessionId) {
        String key = key(userId, sessionId);
        if (key == null) return List.of();
        List<ChatExchange> rows = exchanges.findTop500ByConversationIdOrderByIdDesc(key);
        Collections.reverse(rows);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatExchange row : rows) {
            ChatResponseDto response = readResponse(row.getResponseJson());
            if (response == null) continue;
            result.add(Map.of("from", "user", "text", row.getUserText()));
            Map<String, Object> assistant = new LinkedHashMap<>();
            assistant.put("from", "bot");
            assistant.put("text", response.getAnswer());
            assistant.put("products", response.getProducts());
            assistant.put("chips", response.getChips());
            assistant.put("actions", response.getActions());
            assistant.put("source", response.isFallback() ? "local" : "ai");
            assistant.put("provider", response.getProvider());
            assistant.put("fallback", response.isFallback());
            assistant.put("messageId", response.getMessageId());
            assistant.put("intent", response.getIntent());
            assistant.put("execution", response.getExecution());
            result.add(assistant);
        }
        return result;
    }

    @Transactional(readOnly = true)
    List<Map<String, String>> relevantTurns(Long userId, String sessionId, String question) {
        String key = key(userId, sessionId);
        if (key == null) return List.of();
        List<ChatExchange> recent = exchanges.findTop500ByConversationIdOrderByIdDesc(key);
        if (recent.isEmpty()) return List.of();
        List<ChatExchange> chosen = new ArrayList<>(recent.subList(0, Math.min(4, recent.size())));
        Set<String> words = new HashSet<>(Arrays.asList(new ChatIntentService().normalize(question).split("\\W+")));
        recent.stream().skip(4)
                .filter(row -> {
                    Set<String> old = new HashSet<>(Arrays.asList(new ChatIntentService().normalize(row.getUserText()).split("\\W+")));
                    return words.stream().anyMatch(word -> word.length() >= 4 && old.contains(word));
                }).limit(2).forEach(chosen::add);
        Collections.reverse(chosen);
        return chosen.stream().map(row -> {
            Map<String, String> turn = new LinkedHashMap<>();
            turn.put("user", row.getUserText());
            ChatResponseDto response = readResponse(row.getResponseJson());
            String answer = response == null || response.getAnswer() == null ? "" : response.getAnswer();
            turn.put("assistant", answer.substring(0, Math.min(1200, answer.length())));
            return turn;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void clear(Long userId, String sessionId) {
        String key = key(userId, sessionId);
        if (key == null) return;
        exchanges.deleteByConversationId(key);
        conversations.deleteIfPresent(key);
    }

    private ChatResponseDto readResponse(String json) {
        try { return mapper.readValue(json, ChatResponseDto.class); }
        catch (Exception ex) { return null; }
    }
}

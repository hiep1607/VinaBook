package vn.fs.service;

import java.util.*;
import vn.fs.dto.chat.ChatSearchCriteriaDto;

/** A short-lived cache of the durable conversation state. Eviction never deletes server history. */
final class ChatDialogueMemory {
    private final Map<String, State> sessions = new LinkedHashMap<>();
    synchronized State get(String owner, String session) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.touched > 30 * 60 * 1000L);
        if (session == null || session.isBlank()) return new State();
        String key = (owner == null ? "guest" : "user:" + owner) + ":" + session;
        State state = sessions.get(key);
        if (state == null) {
            if (sessions.size() >= 2000) sessions.remove(sessions.keySet().iterator().next());
            state = new State(); sessions.put(key, state);
        }
        state.touched = now;
        return state;
    }
    synchronized void clear(String owner, String session) {
        if (session != null) sessions.remove((owner == null ? "guest" : "user:" + owner) + ":" + session);
    }
    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    static final class State {
        @com.fasterxml.jackson.annotation.JsonIgnore
        boolean loaded;
        @com.fasterxml.jackson.annotation.JsonIgnore
        long touched = System.currentTimeMillis();
        boolean initialized;
        String query = "", goal = "", level = "", missingTitle = "";
        ChatSearchCriteriaDto criteria = ChatSearchCriteriaDto.empty();
        List<Long> displayed = new ArrayList<>(), shortlist = new ArrayList<>();
        List<Long> comparisonProducts = new ArrayList<>();
        List<Long> discoverySeeds = new ArrayList<>(), discoveryExact = new ArrayList<>(), discoveryNearby = new ArrayList<>();
        Map<Long,String> discoveryReasons = new LinkedHashMap<>();
        Integer discoveryRequestedCount;
        int discoveryEmptyRounds;
		Set<Long> shownProductIds = new LinkedHashSet<>();
        int recommendationHistoryVersion;
        Long focus;
        Long recommendationBudget;
        String recommendationNeed = "", recommendationReader = "";
        boolean recommendationPending, recommendationFactsLoaded;
        @com.fasterxml.jackson.annotation.JsonIgnore
        boolean recommendationIntakeRequired;
        List<String> recommendationTurns = new ArrayList<>();
        Long focusedOrderId;
        boolean awaitingPurchase;
        boolean awaitingComparison;
        boolean emptySearch;
        String offered = "", alternativeFor = "";
        String lastTask = "";
        String pendingQuestion = "";
        String pendingReviewMode = "";
        Long pendingReviewProductId;
        String returnReason = "";
        boolean returnActive, returnAwaitingReason;
        List<Long> explanationProductIds = new ArrayList<>();
        int explanationRound;
        Long comparisonAnchor;
        Map<String, Object> clarificationFacts = new LinkedHashMap<>();
        // Request identity handles a stream->JSON retry without repeating state transitions.
        @com.fasterxml.jackson.annotation.JsonIgnore
        String lastRequest;
        Map<String, Object> toolSearch = new LinkedHashMap<>();
        List<Map<String, String>> toolHistory = new ArrayList<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        vn.fs.dto.chat.ChatResponseDto lastResponse;
        @com.fasterxml.jackson.annotation.JsonIgnore
        Map<String, Object> accountMemory = new LinkedHashMap<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        List<Map<String, String>> recentDialogue = new ArrayList<>();
    }
}

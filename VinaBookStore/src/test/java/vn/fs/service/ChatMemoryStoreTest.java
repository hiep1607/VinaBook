package vn.fs.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatResponseDto;
import vn.fs.dto.chat.ChatSearchCriteriaDto;
import vn.fs.entity.ChatConversation;
import vn.fs.entity.ChatExchange;
import vn.fs.repository.ChatConversationRepository;
import vn.fs.repository.ChatExchangeRepository;

class ChatMemoryStoreTest {
    private static final String SESSION = "chat-0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test void stateAndTranscriptSurviveCacheRecreationAndRequestRetry() {
        ChatConversationRepository conversations = mock(ChatConversationRepository.class);
        ChatExchangeRepository exchanges = mock(ChatExchangeRepository.class);
        AtomicReference<ChatConversation> savedConversation = new AtomicReference<>();
        AtomicReference<ChatExchange> savedExchange = new AtomicReference<>();
        when(conversations.findById(any())).thenAnswer(call -> Optional.ofNullable(savedConversation.get()));
        when(conversations.save(any())).thenAnswer(call -> { savedConversation.set(call.getArgument(0)); return call.getArgument(0); });
        when(exchanges.findByConversationIdAndRequestId(any(), any())).thenAnswer(call -> Optional.ofNullable(savedExchange.get()));
        when(exchanges.save(any())).thenAnswer(call -> { savedExchange.set(call.getArgument(0)); return call.getArgument(0); });
        when(exchanges.findTop500ByConversationIdOrderByIdDesc(any())).thenAnswer(call ->
                savedExchange.get() == null ? new ArrayList<>() : new ArrayList<>(List.of(savedExchange.get())));
        ChatMemoryStore store = new ChatMemoryStore(conversations, exchanges, new ObjectMapper());
        ChatDialogueMemory.State first = new ChatDialogueMemory.State();
        first.initialized = true;
        first.displayed = List.of(57L, 58L);
        first.shortlist = List.of(57L, 58L);
        first.comparisonProducts = List.of(57L, 58L);
        first.focus = 58L;
        first.goal = "học Python";
        first.shownProductIds.addAll(List.of(57L,58L,9L));
        first.discoverySeeds=List.of(57L,58L);
        first.discoveryExact=List.of(57L);
        first.discoveryNearby=List.of(9L);
        first.discoveryReasons=Map.of(9L,"Có nội dung gần với nhu cầu đọc.");
        first.discoveryRequestedCount=6;
        ChatResponseDto answer = new ChatResponseDto("Đây là hai cuốn phù hợp.", "local", "", false,
                List.of(), List.of(), "message-1", ChatIntent.DISCOVER_BOOKS, ChatSearchCriteriaDto.empty(), List.of());
        store.save(7L, SESSION, "request-1", "Gợi ý hai cuốn", answer, first, true);

        ChatDialogueMemory.State reloaded = new ChatDialogueMemory.State();
        store.restore(7L, SESSION, reloaded);
        assertTrue(reloaded.initialized);
        assertEquals(List.of(57L, 58L), reloaded.shortlist);
        assertEquals(List.of(57L, 58L), reloaded.comparisonProducts);
        assertEquals(58L, reloaded.focus);
        assertEquals("học Python", reloaded.goal);
        assertEquals(first.shownProductIds,reloaded.shownProductIds);
        assertEquals(first.discoverySeeds,reloaded.discoverySeeds);
        assertEquals(first.discoveryExact,reloaded.discoveryExact);
        assertEquals(first.discoveryNearby,reloaded.discoveryNearby);
        assertEquals(first.discoveryReasons,reloaded.discoveryReasons);
        assertEquals(6,reloaded.discoveryRequestedCount);
        assertEquals("Đây là hai cuốn phù hợp.", store.duplicate(7L, SESSION, "request-1").getAnswer());
        assertEquals(2, store.history(7L, SESSION).size());
    }

    @Test void conversationKeysKeepAccountsApartAndRejectWeakGuestTokens() {
        assertNotEquals(ChatMemoryStore.key(7L, SESSION), ChatMemoryStore.key(8L, SESSION));
        assertNotEquals(ChatMemoryStore.key(7L, SESSION), ChatMemoryStore.key(null, SESSION));
        assertNull(ChatMemoryStore.key(null, "chat-m123-abcdef12"));
        assertNotNull(ChatMemoryStore.key(null, SESSION));
    }
    @Test void oldSnapshotRebuildsShownBooksFromTheWholeSavedConversation() throws Exception {
        ChatConversationRepository conversations=mock(ChatConversationRepository.class);
        ChatExchangeRepository exchanges=mock(ChatExchangeRepository.class);
        ChatConversation saved=new ChatConversation();saved.setStateJson("{\"shownProductIds\":[58],\"initialized\":true}");
        when(conversations.findById(any())).thenReturn(Optional.of(saved));
        ChatExchange old=new ChatExchange();old.setResponseJson("{\"answer\":\"Sách đã giới thiệu từ trước\",\"products\":[{\"id\":57,\"name\":\"Sách A\"}]}");
        when(exchanges.findByConversationIdOrderByIdAsc(any())).thenReturn(List.of(old));
        ChatDialogueMemory.State restored=new ChatDialogueMemory.State();
        new ChatMemoryStore(conversations,exchanges,new ObjectMapper()).restore(7L,SESSION,restored);
        assertTrue(restored.shownProductIds.containsAll(List.of(57L,58L)));
        assertEquals(1,restored.recommendationHistoryVersion);
        restored.loaded=false;
        saved.setStateJson(new ObjectMapper().setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY).writeValueAsString(restored));
        new ChatMemoryStore(conversations,exchanges,new ObjectMapper()).restore(7L,SESSION,new ChatDialogueMemory.State());
        verify(exchanges,times(1)).findByConversationIdOrderByIdAsc(any());
    }
}

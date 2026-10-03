package vn.fs.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.entity.Category;
import vn.fs.entity.ChatAccountMemory;
import vn.fs.entity.Product;
import vn.fs.repository.ChatAccountMemoryRepository;
import vn.fs.repository.FavoriteRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.repository.UserRepository;

class ChatAccountMemoryServiceTest {
    @Test void explicitPreferenceAndBookViewPersistAcrossConversations() {
        ChatAccountMemoryRepository memories = mock(ChatAccountMemoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        AtomicReference<ChatAccountMemory> saved = new AtomicReference<>();
        when(memories.findById(7L)).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(memories.save(any())).thenAnswer(call -> { saved.set(call.getArgument(0)); return call.getArgument(0); });
        Product book = new Product();
        book.setStatus(true);
        book.setCategory(new Category(1L, "Lập trình"));
        when(products.findByProductIdAndStatusTrue(57L)).thenReturn(book);
        UserRepository users = mock(UserRepository.class);
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        ChatAccountMemoryService first = new ChatAccountMemoryService(memories, products, users, favorites, new ObjectMapper());
        first.observeChat(7L, "Tôi thích sách Python cho người mới", ChatIntent.DISCOVER_BOOKS);
        first.observeProductView(7L, 57L);

        ChatAccountMemoryService nextConversation = new ChatAccountMemoryService(memories, products, users, favorites, new ObjectMapper());
        Map<String, Object> context = nextConversation.context(7L);
        assertTrue(context.get("explicitInterests").toString().contains("Python"));
        assertTrue(context.get("viewedCategories").toString().contains("Lập trình"));
        assertEquals(Map.of(), nextConversation.context(null));
    }
}

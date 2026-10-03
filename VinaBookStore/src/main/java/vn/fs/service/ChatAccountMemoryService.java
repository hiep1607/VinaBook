package vn.fs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.dto.chat.ChatIntent;
import vn.fs.entity.ChatAccountMemory;
import vn.fs.entity.Favorite;
import vn.fs.entity.Product;
import vn.fs.repository.ChatAccountMemoryRepository;
import vn.fs.repository.FavoriteRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.repository.UserRepository;

/** Small, account-owned reading profile. Current requests always outrank these weak signals. */
@Service
public class ChatAccountMemoryService {
    private static final Pattern PREFERENCE = Pattern.compile("(?iu)\\b(?:tôi|mình|em)\\s+(không thích|tránh|thích|quan tâm|ưu tiên)\\s+([^.!?\\n]{3,90})");
    private final ChatAccountMemoryRepository memories;
    private final ProductRepository products;
    private final UserRepository users;
    private final FavoriteRepository favorites;
    private final ObjectMapper mapper;

    public ChatAccountMemoryService(ChatAccountMemoryRepository memories, ProductRepository products,
            UserRepository users, FavoriteRepository favorites, ObjectMapper mapper) {
        this.memories = memories;
        this.products = products;
        this.users = users;
        this.favorites = favorites;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> context(Long userId) {
        if (userId == null) return Map.of();
        Profile profile = read(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("explicitInterests", profile.interests.subList(Math.max(0, profile.interests.size() - 20), profile.interests.size()));
        result.put("explicitAvoids", profile.avoids.subList(Math.max(0, profile.avoids.size() - 20), profile.avoids.size()));
        result.put("usualBudgetCeiling", profile.usualBudgetCeiling);
        result.put("viewedCategories", profile.categoryViews.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .map(Map.Entry::getKey).collect(Collectors.toList()));
        List<Map<String, Object>> favoriteBooks = new ArrayList<>();
        users.findById(userId).ifPresent(user -> {
            for (Favorite favorite : favorites.findByUser(user)) {
                Product book = favorite.getProduct();
                if (book == null || !Boolean.TRUE.equals(book.getStatus()) || favoriteBooks.size() >= 8) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", book.getProductId());
                item.put("name", book.getName());
                item.put("category", book.getCategory() == null ? "" : book.getCategory().getCategoryName());
                favoriteBooks.add(item);
            }
        });
        result.put("favorites", favoriteBooks);
        return result;
    }

    @Transactional
    public void observeChat(Long userId, String question, ChatIntent intent) {
        if (userId == null || question == null || question.length() > 1000 || intent == ChatIntent.ORDER_SUPPORT) return;
        Matcher matcher = PREFERENCE.matcher(question);
        Profile profile = null;
        boolean changed = false;
        while (matcher.find()) {
            String value = matcher.group(2).trim().replaceAll("(?iu)^(?:đọc|sách|về)\\s+", "");
            if (value.length() < 3 || value.length() > 80 || sensitive(value)) continue;
            if (profile == null) profile = read(userId);
            boolean avoid = matcher.group(1).toLowerCase(Locale.ROOT).contains("không") || matcher.group(1).equalsIgnoreCase("tránh");
            List<String> target = avoid ? profile.avoids : profile.interests;
            target.removeIf(existing -> existing.equalsIgnoreCase(value));
            target.add(value);
            changed = true;
        }
        String normalized = new ChatIntentService().normalize(question);
        if (normalized.matches("(?s).*(?:ngan sach (?:thuong|cua toi)|toi thuong mua sach).*")) {
            List<Long> amounts = ChatMoney.parse(normalized);
            if (!amounts.isEmpty()) {
                if (profile == null) profile = read(userId);
                profile.usualBudgetCeiling = amounts.get(0);
                changed = true;
            }
        }
        if (changed) write(userId, profile);
    }

    @Transactional
    public void observeProductView(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        Product book = products.findByProductIdAndStatusTrue(productId);
        if (book == null || book.getCategory() == null) return;
        Profile profile = read(userId);
        String category = book.getCategory().getCategoryName();
        if (category == null || category.isBlank()) return;
        profile.categoryViews.merge(category, 1, (oldValue, one) -> Math.min(100, oldValue + one));
        write(userId, profile);
    }

    private boolean sensitive(String text) {
        return text.matches("(?is).*(?:\\b0[0-9 .-]{9,14}\\b|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|otp|mật khẩu|địa chỉ|số nhà|đơn hàng|thẻ ngân hàng).*");
    }

    private Profile read(Long userId) {
        try {
            return memories.findById(userId).map(row -> {
                try {
                    Profile profile = mapper.readValue(row.getProfileJson(), Profile.class);
                    if (profile.interests == null) profile.interests = new ArrayList<>();
                    if (profile.avoids == null) profile.avoids = new ArrayList<>();
                    if (profile.categoryViews == null) profile.categoryViews = new LinkedHashMap<>();
                    return profile;
                }
                catch (Exception ex) { return new Profile(); }
            }).orElseGet(Profile::new);
        } catch (RuntimeException ex) { return new Profile(); }
    }

    private void write(Long userId, Profile profile) {
        try {
            ChatAccountMemory row = memories.findById(userId).orElseGet(ChatAccountMemory::new);
            row.setUserId(userId);
            row.setProfileJson(mapper.writeValueAsString(profile));
            row.setUpdatedAt(LocalDateTime.now());
            memories.save(row);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot persist reading preferences", ex);
        }
    }

    public static class Profile {
        public List<String> interests = new ArrayList<>();
        public List<String> avoids = new ArrayList<>();
        public Long usualBudgetCeiling;
        public Map<String, Integer> categoryViews = new LinkedHashMap<>();
    }
}

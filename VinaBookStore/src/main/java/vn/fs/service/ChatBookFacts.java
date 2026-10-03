package vn.fs.service;

import java.util.*;
import vn.fs.entity.Product;

/** Requested facts are rendered from store data, independently of the planner's field choice. */
final class ChatBookFacts {
    private ChatBookFacts() {}

    static Set<String> requested(String question, List<Product> books, ChatIntentService language) {
        String q = language.normalize(question);
        for (Product book : books) {
            String title = language.normalize(book.getName()).replaceAll("\\s*\\([^)]*\\)", "").trim();
            q = q.replace(title, " ");
            String shortTitle = title.split("\\s+[-–:]\\s+", 2)[0];
            if (shortTitle.length() >= 4) q = q.replace(shortTitle, " ");
        }
        Set<String> fields = new LinkedHashSet<>();
        if (has(q, "tac gia|ai viet|cua ai")) fields.add("author");
        if (has(q, "gia|bao nhieu tien|re hon|re nhat|dat hon|dat nhat")) fields.add("price");
        if (has(q, "con hang|con ban|dang ban|co ban|ton kho|con may|bao nhieu ban|bao nhieu cuon")) fields.add("inventory");
        if (has(q, "rating|danh gia|[0-5](?:[.,]\\d)? sao")) fields.add("rating");
        if (has(q, "noi dung|tom tat|ke ve")) fields.add("description");
        if (has(q, "nha xuat ban|nxb")) fields.add("publisher");
        if (has(q, "nam xuat ban")) fields.add("publicationYear");
        if (has(q, "so trang|bao nhieu trang")) fields.add("pageCount");
        if (has(q, "isbn")) fields.add("isbn");
        return fields;
    }

    static boolean has(String normalized, String alternatives) {
        return normalized.matches("(?s).*\\b(?:" + alternatives + ")\\b.*");
    }

    static final Set<String> BIBLIOGRAPHY = Set.of("publisher", "publicationYear", "pageCount", "isbn");

    static String missingBibliography(Set<String> fields) {
        List<String> missing = new ArrayList<>();
        if (fields.contains("publisher")) missing.add("nhà xuất bản");
        if (fields.contains("publicationYear")) missing.add("năm xuất bản");
        if (fields.contains("pageCount")) missing.add("số trang");
        if (fields.contains("isbn")) missing.add("ISBN");
        return "Chưa có dữ liệu đã duyệt về " + String.join(", ", missing) + ".";
    }
}

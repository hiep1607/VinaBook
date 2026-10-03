package vn.fs.service;

import org.junit.jupiter.api.Test;
import vn.fs.entity.Product;
import static org.assertj.core.api.Assertions.assertThat;

class ChatExplicitFiltersTest {
    private final ChatExplicitFilters filters = new ChatExplicitFilters(new ChatIntentService());
    private Product book(String name, String description) {
        Product p = new Product(); p.setName(name); p.setDescription(description); return p;
    }
    @Test void ratingsNeverOverrideTopic() {
        assertThat(filters.matches("Sách lập trình dưới 200k từ 4 sao", book("Bố Già", "Một tác phẩm được đánh giá cao"))).isFalse();
        assertThat(filters.matches("Sách lập trình dưới 200k từ 4 sao", book("Java cơ bản", "Học lập trình"))).isTrue();
    }
    @Test void alternativeLanguagesUseOrWithoutDroppingTopic() {
        assertThat(filters.matches("Java hoặc Python dưới 180k", book("Java cơ bản", "Hướng dẫn Java"))).isTrue();
        assertThat(filters.matches("Java hoặc Python dưới 180k", book("Truyện Kiều", "Văn học"))).isFalse();
        assertThat(filters.matches("Java và Python", book("Java cơ bản", "Hướng dẫn Java"))).isFalse();
    }
    @Test void excludedLanguagesAreNotPositiveRequirements() {
        assertThat(filters.matches("Lập trình, không lấy Java, chỉ lấy Python",book("Python Cơ Bản","Học lập trình Python"))).isTrue();
        assertThat(filters.matches("Lập trình, không lấy Java, chỉ lấy Python",book("Java Cơ Bản","Học lập trình Java"))).isFalse();
        assertThat(filters.matches("Không lấy Java",book("Java Cơ Bản","Học lập trình Java"))).isFalse();
    }
    @Test void childrenAndFrameworkRequirementsRemainMandatory() {
		assertThat(filters.matches("5 sách Công nghệ thông tin rẻ nhất", book("Kinh tế học", "Sử dụng dữ liệu để giải thích kinh doanh"))).isFalse();
        assertThat(filters.matches("Sách trẻ em từ 4 sao", book("Luật lao động", "Hướng dẫn pháp lý"))).isFalse();
        assertThat(filters.matches("Học Spring Boot", book("Clean Code", "Mã nguồn sạch"))).isFalse();
    }
    @Test void programmingRequestExcludesGeneralTechnologyBook() {
        assertThat(filters.matches("Chọn 2 sách học lập trình", book("10 Vạn Câu Hỏi Vì Sao",
                "Giải đáp về thiên nhiên, cơ thể và công nghệ cho trẻ"))).isFalse();
        assertThat(filters.matches("Chọn 2 sách học lập trình", book("Cấu Trúc Dữ Liệu",
                "Giáo trình cấu trúc dữ liệu và thuật toán"))).isTrue();
        assertThat(filters.matches("Chọn 2 sách học lập trình", book("Java Cơ Bản",
                "Học Java cho người mới"))).isTrue();
        assertThat(filters.matches("Sách công nghệ cho trẻ", book("10 Vạn Câu Hỏi Vì Sao",
                "Giải đáp về thiên nhiên, cơ thể và công nghệ cho trẻ"))).isTrue();
    }
    @Test void codeDoesNotMeanCashOnDelivery() {
        ChatIntentService intents = new ChatIntentService();
        assertThat(intents.classify("Viết code Python")).isNotEqualTo(vn.fs.dto.chat.ChatIntent.POLICY);
        assertThat(intents.classify("Có thanh toán COD không?")).isEqualTo(vn.fs.dto.chat.ChatIntent.POLICY);
        assertThat(intents.classify("Tìm sách của tác giả Nguyễn Nhật Ánh")).isEqualTo(vn.fs.dto.chat.ChatIntent.DISCOVER_BOOKS);
        assertThat(intents.classify("So sánh hai sách đánh giá cao nhất")).isEqualTo(vn.fs.dto.chat.ChatIntent.COMPARE_BOOKS);
    }
    @Test void sourceCodeAndSoftwareEngineeringGoalsRequireProgrammingContent() {
        assertThat(filters.matches("Muốn mã nguồn dễ bảo trì",book("Luật dân sự","Giải thích các điều luật dễ hiểu"))).isFalse();
        assertThat(filters.matches("Đọc gì để viết mã dễ hiểu",book("Clean Code","Cách viết mã nguồn sạch"))).isTrue();
        assertThat(filters.matches("Học cách trở thành kỹ sư phần mềm",book("Pragmatic Programmer","Công cụ cho thợ code"))).isTrue();
    }
}

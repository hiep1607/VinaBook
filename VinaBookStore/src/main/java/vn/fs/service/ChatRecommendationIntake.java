package vn.fs.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.fs.dto.chat.ChatIntent;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.entity.Product;

/** Required recommendation facts come from customer text, never model guesses or browsing. */
final class ChatRecommendationIntake {
    record Decision(String question, String message) {}
    private final ChatIntentService language;
    private final ChatCatalogQuery search;

    ChatRecommendationIntake(ChatIntentService language, ChatRetrievalService retrieval) {
        this.language = language;
        this.search = new ChatCatalogQuery(language, retrieval);
    }

    Decision prepare(ChatRequestDto request, ChatDialogueMemory.State state, List<Product> catalog) {
        String message = request.getMessage(), q = language.normalize(message);
        if (exempt(message, catalog)) {
            if (!search.named(message,catalog).isEmpty() || !search.quoted(message).isBlank())
                observe(message,state,catalog,false);
            return new Decision("", message);
        }
        boolean discovery = discovery(message) || state.recommendationPending;
        if (!discovery) return new Decision("", message);
        // Restore explicit customer facts for existing conversations; assistant replies are not evidence.
        if (!state.recommendationFactsLoaded) {
            request.getHistory().stream().filter(t -> "user".equals(t.getRole()))
                .filter(t -> !ChatService.privateText(t.getContent()))
                .forEach(t -> observe(t.getContent(), state, catalog, false));
            state.recommendationFactsLoaded = true;
        }
        observe(message, state, catalog, state.recommendationPending);
        if (!ready(state)) {
            remember(message, state);
            state.recommendationPending = true;
            return new Decision(question(state), message);
        }
        if (state.recommendationPending) {
            remember(message, state);
            message = String.join(". ", state.recommendationTurns);
            state.recommendationTurns.clear();
            state.recommendationPending = false;
        } else if (need(message, catalog, false).isBlank() && !language.wantsAnotherBook(message)
            && (language.isBudgetOnlyDiscovery(message) || ChatBookFacts.has(q,"goi y|gioi thieu|tu van|tim sach"))) {
            message = message + ". " + state.recommendationNeed;
        }
        if (language.classify(message) == ChatIntent.UNKNOWN) message = "Gợi ý sách. " + message;
        // Supply confirmed context on a short continuation, without inventing a price limit.
        if (reader(message).isBlank()) message += ". Sách " + state.recommendationReader;
        return new Decision("", message);
    }

    boolean exempt(String message, List<Product> catalog) {
        String q = language.normalize(message);
        ChatIntent intent = language.classify(message);
        if(!ChatCustomerActions.wishlistTool(q).isEmpty())return true;
        if(ChatCustomerActions.reorder(q))return true;
        if(ChatCustomerActions.returnRequest(q))return true;
        if(!ChatCustomerActions.supportTool(q).isEmpty())return true;
        boolean suggestion = ChatBookFacts.has(q, "goi y|tu van|nen doc|nen mua|doc gi|tuong tu|cuon khac|sach khac");
        if (!suggestion && (!search.named(message, catalog).isEmpty() || !search.quoted(message).isBlank())) return true;
        if (ChatService.isPurchaseRequest(q) || !ChatOrchestrator.cartTool(q).isEmpty()) return true;
        if (!suggestion && q.contains("gio hang") && ChatService.isCartChangeRequest(q)) return true;
        return intent == ChatIntent.ORDER_SUPPORT || intent == ChatIntent.SMALL_TALK
            || intent == ChatIntent.COMPARE_BOOKS || intent == ChatIntent.PRODUCT_INFO && !suggestion
            || intent == ChatIntent.POLICY && !discovery(message)
            || q.matches(".*\\b(?:nhung the loai nao|cac the loai nao|co the loai nao|co nhung the loai)\\b.*");
    }

    private boolean discovery(String message) {
        String q = language.normalize(message);
        return language.classify(message) == ChatIntent.DISCOVER_BOOKS
            || !language.criteria(message,List.of()).getTopics().isEmpty()
            || ChatBookFacts.has(q, "goi y|gioi thieu sach|tu van sach|tuong tu|tieu thuyet|truyen|doc de|muon hoc|can hoc|muon tim hieu");
    }

    private void observe(String message, ChatDialogueMemory.State state, List<Product> catalog, boolean pending) {
        String recipient = reader(message), need = need(message, catalog, pending);
        if (!recipient.isBlank()) {
            // A new recipient needs their own reading need; do not transfer the previous person's tastes.
            if (!state.recommendationReader.isBlank() && !recipient.equals(state.recommendationReader) && need.isBlank())
                state.recommendationNeed = "";
            state.recommendationReader = recipient;
        }
        if (!need.isBlank()) state.recommendationNeed = withoutBudget(need);
    }

    private String reader(String message) {
        String q = language.normalize(message);
        if (q.matches("(?:cho )?(?:toi|minh|t|em)[.!?]*")) return "cho mình";
        Matcher recipient = Pattern.compile("\\b(?:cho|tang|danh cho|doc cho)\\s+((?:toi|minh|t|em)(?!\\s+(?:gai|trai|nho))|(?:ban|be|con|me|bo|ong|ba|nguoi|tre|hoc sinh|sinh vien)\\b[^,.!?]{0,65})").matcher(q);
        if (recipient.find()) {
            String value = recipient.group(1).trim();
            return value.matches("toi|minh|t|em") ? "cho mình" : "cho " + value;
        }
        Matcher audience = Pattern.compile("\\b(?:nguoi moi(?: bat dau)?|nguoi it doc|tre em|thieu nhi|hoc sinh|sinh vien|(?:be|con|em)\\s+(?:[0-9]{1,2}\\s+tuoi|gai|trai))\\b").matcher(q);
        if (audience.find()) return "cho " + audience.group();
        if (!ChatBookFacts.has(q, "tang|lam qua|mua qua")
            && q.matches(".*\\b(?:toi|minh|t|em)\\s+(?:muon|can|thich|doc|dang|moi|chua|khong|it|hay|la|hoc|nen)\\b.*")) return "cho mình";
        return "";
    }

    private String need(String message, List<Product> catalog, boolean pending) {
        String q = language.normalize(message);
        var criteria = language.criteria(message, catalog);
        boolean subject = criteria.getTopics().stream().anyMatch(t -> !List.of("làm quà", "giáo trình", "thiếu nhi").contains(t))
            || !criteria.getAuthor().isBlank() || !criteria.getCategory().isBlank() && !q.matches(".*\\b(?:thieu nhi|tre em)\\b.*");
        if (subject || !search.named(message,catalog).isEmpty() || ChatBookFacts.has(q, "truyen|tieu thuyet|tho|hoi hoa|am thuc|nau an|toan|vat ly|sinh hoc|thiet ke|thu gian|giai tri|giam stress|nhe nhang|de hieu|de doc")
            || q.matches(".*\\b(?:sach ve|doc ve|doc de|hoc ve|hoc cach|tim hieu ve|quan tam)\\s+\\S+.*"))
            return ChatDialogueInterpreter.clean(message, 500);
        // Accept a free-text answer to our subject question, but never treat a budget or recipient as a subject.
        if (pending && reader(message).isBlank() && ChatMoney.parse(q).isEmpty() && q.length() >= 4
            && !ChatBookFacts.has(q, "khong biet|chua biet|khong ro|tuy ban|tuy y|bat ky|gi cung|sao cung|sach hay|goi y|ngan sach|bao nhieu|khong gioi han|khong co|them lua chon|cuon khac|sach khac|tai sao|vi sao|la sao"))
            return ChatDialogueInterpreter.clean(message, 500);
        return "";
    }

    static boolean ready(ChatDialogueMemory.State state) {
        return !state.recommendationNeed.isBlank() && !state.recommendationReader.isBlank();
    }

    static String question(ChatDialogueMemory.State state) {
        return ChatVoice.intakeQuestion(state);
    }

    private void remember(String message, ChatDialogueMemory.State state) {
        if (!ChatMoney.parse(language.normalize(message)).isEmpty())
            state.recommendationTurns.replaceAll(this::withoutBudget);
        state.recommendationTurns.add(ChatDialogueInterpreter.clean(message, 1000));
        if (state.recommendationTurns.size() > 6) state.recommendationTurns.remove(1);
    }

    private String withoutBudget(String message) {
        String q=language.normalize(message);
        if (ChatMoney.parse(q).isEmpty()) return message;
        return q.replaceAll("\\b(?:(?:ngan sach(?: cua toi)?|toi co|gia|duoi|tren|khoang|tam|toi da|toi thieu|tu|den)\\s+)*[0-9]+(?:[.,][0-9]+)*\\s*(?:trieu|nghin|ngan|dong|vnd|lit|tr|k|d)?\\b(?!\\s*(?:tuoi|cuon|quyen|ban|trang|nam|sao)\\b)"," ")
            .replaceAll("\\s+"," ").trim();
    }
}

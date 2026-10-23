package vn.fs.service;

import static vn.fs.service.ChatTool.Parameter.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import vn.fs.dto.chat.*;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

/** Grounded book explanations, separate from tool registration and catalog selection. */
final class ChatBookPresenter {
    private final ChatBuiltinTools owner;
    ChatBookPresenter(ChatBuiltinTools owner){this.owner=owner;}
    ChatTool.Result explainSelection(List<Product> selected,ChatTool.Context c) {
        String original=Objects.toString(c.state().toolSearch.get("query"),"");
        if(ChatConversationSupport.audienceOnly(original,owner.language))return ChatTool.Result.clarify(
            "Gợi ý trước chưa có đủ căn cứ về sở thích của người đọc. Mình không có thống kê sách được yêu thích theo giới tính nên không thể nói các cuốn đó đặc biệt hợp với các bạn nữ. Người bạn đang tìm sách cho thích chủ đề gì để mình chọn lại phù hợp hơn?");
        List<Long> current=owner.ids(selected);
        c.state().explanationRound=current.equals(c.state().explanationProductIds)?c.state().explanationRound+1:1;
        c.state().explanationProductIds=new ArrayList<>(current);
        boolean contentDepth=ChatBookFacts.has(owner.language.normalize(c.request().getMessage()),
            "cu the hon|chi tiet hon|noi ky hon|noi ro hon|sau hon");
        String opening=selected.size()==1?"Về nội dung cuốn này:":"Về nội dung các cuốn vừa xem:";
        StringBuilder answer=new StringBuilder(opening);
        for(Product book:selected) {
            Map<String,String> facts=owner.readingFacts(book);
            List<String> missing=new ChatReadingFit(owner.language).missing(c.state().toolSearch,facts);
            String summary=ChatDialogueInterpreter.clean(facts.getOrDefault("summary",""),c.state().explanationRound==1 && !contentDepth?450:900);
            answer.append("\n\n• “").append(book.getName()).append("”\n");
            if(c.state().discoveryNearby.contains(book.getProductId()))
                answer.append("Đây là lựa chọn gần với yêu cầu, để bạn cân nhắc bổ sung. ")
                    .append(c.state().discoveryReasons.getOrDefault(book.getProductId(),"Nội dung chỉ đáp ứng một phần hướng đọc ban đầu.")).append(" ");
            else if(c.state().discoveryExact.contains(book.getProductId()) && !original.isBlank())
                answer.append(missing.isEmpty()?"Cuốn này nằm trong hướng đọc bạn đang tìm. ":"Mình chọn cuốn này theo phần nội dung đã được mô tả. ");
            answer.append(summary.isBlank()?"Cửa hàng chưa có mô tả nội dung đủ để giải thích sự phù hợp của cuốn này.":summary);
            if(!missing.isEmpty())answer.append("\nMình chưa đủ thông tin để xác nhận ").append(String.join(", ",missing)).append(" cho cuốn này.");
            String goal=owner.language.normalize(original+" "+owner.str(c.state().toolSearch,"semanticQuery"));
            String content=owner.language.normalize(facts.getOrDefault("summary",""));
            if(ChatBookFacts.has(owner.language.normalize(c.request().getMessage()),"lap ngan sach|chi tieu hang thang")
                    && !ChatBookFacts.has(content,"lap ngan sach|chi tieu hang thang"))
                answer.append("\nChưa có thông tin đã duyệt xác nhận sách hướng dẫn lập ngân sách chi tiêu hàng tháng. Phần nội dung trên là phạm vi mình có thể xác nhận, nên mình chưa chọn cuốn này làm tài liệu cho mục tiêu lập ngân sách của bạn.");
            if(ChatBookFacts.has(goal,"giao tiep|ung xu") && ChatBookFacts.has(content,"doi nhan xu the|giao tiep")
                    && c.state().explanationRound>1 && !ChatBookFacts.has(content,"mot nguyen tac.*la|vi du cu the|tinh huong thuc te"))
                answer.append("\nĐiểm để cân nhắc là phạm vi ứng xử trong quan hệ với người khác. Mô tả chưa nêu từng nguyên tắc hoặc ví dụ, nên mình chưa thể xác nhận cuốn này hướng dẫn riêng về thuyết trình hay đàm phán.");
            if(new ChatReadingFit(owner.language).needs(c.state().toolSearch).contains("accessible") && !missing.isEmpty()
                    && ChatBookFacts.has(content,"tuoi tho|tinh ban"))
                answer.append("\nNếu bạn muốn bắt đầu từ câu chuyện về ").append(ChatBookFacts.has(content,"tuoi tho")?"tuổi thơ":"tình bạn")
                    .append(", đây là điểm nội dung để cân nhắc khi xem thử.");
            String currentQuestion=owner.language.normalize(c.request().getMessage());
            List<String> detailKeys=ChatBookFacts.has(currentQuestion,"do tuoi|tuoi ro|danh cho|doi tuong")?List.of("audience")
                :ChatBookFacts.has(currentQuestion,"de doc|muc doc")?List.of("readingLevel")
                :ChatBookFacts.has(currentQuestion,"phong cach|cach trinh bay|loi viet")?List.of("readingStyle")
                :ChatBookFacts.has(currentQuestion,"muc tieu doc")?List.of("learningGoal")
                :c.state().explanationRound==2?List.of("learningGoal","audience"):List.of();
            for(String key:detailKeys) {
                String value=facts.getOrDefault(key,"");
                if(!value.isBlank() && !summary.contains(value)
                        && c.state().recentDialogue.stream().noneMatch(turn->owner.language.normalize(turn.getOrDefault("assistant","")).contains(owner.language.normalize(value))))answer.append("\n")
                    .append(switch(key){case "learningGoal"->"Mục tiêu đọc: ";case "audience"->"Đối tượng: ";case "readingLevel"->"Mức đọc: ";default->"Cách trình bày: ";})
                    .append(ChatDialogueInterpreter.clean(value,240));
            }
            if(!detailKeys.isEmpty() && c.state().explanationRound>1 && facts.getOrDefault("readingLevel","").isBlank() && facts.getOrDefault("readingStyle","").isBlank())
                answer.append("\nPhần mô tả chưa xác nhận mức đọc hoặc cách trình bày, nên nếu bạn đang cân nhắc hai điểm này thì cần thêm thông tin trước khi chọn.");
        }
        String selectionQuestion=owner.language.normalize(c.request().getMessage());
        ChatReadingFit selectionFit=new ChatReadingFit(owner.language);
        boolean asksReadability=ChatBookFacts.has(selectionQuestion,"cuon nao.*de doc.*nhat");
        boolean asksAge=ChatBookFacts.has(selectionQuestion,"cuon nao.*(?:do tuoi|tuoi ro).*nhat");
        if(asksReadability || asksAge && c.state().toolSearch.containsKey("readerAge")) {
            List<Product> confirmed=selected.stream().filter(b->asksReadability
                ?selectionFit.confirmed("accessible",owner.readingFacts(b))
                :selectionFit.ageConfirmed(c.state().toolSearch,owner.readingFacts(b))).collect(Collectors.toList());
            if(confirmed.size()==1) {
                Product chosen=confirmed.get(0);c.state().focus=chosen.getProductId();
                answer.append("\n\nTrong danh sách vừa xem, “").append(chosen.getName())
                    .append(asksReadability?"” là cuốn duy nhất có thông tin đã duyệt xác nhận độ dễ đọc. Đây là lựa chọn có căn cứ rõ hơn cho nhu cầu người ít đọc của bạn.":
                        "” là cuốn duy nhất có phân loại độ tuổi đã được đối chiếu phù hợp với tuổi bạn nêu.");
            }
        }
        if(c.state().explanationRound>2)answer.append("\n\nMình chưa có thêm dữ kiện về điểm còn thiếu thông tin. Bạn muốn đối chiếu ý nào với một tình huống cụ thể?");
        if(contentDepth && c.state().explanationRound==1 && selected.stream().allMatch(b->{
                Map<String,String> facts=owner.readingFacts(b);
                return facts.getOrDefault("summary","").length()<450
                    && List.of("learningGoal","audience","readingLevel","readingStyle").stream()
                        .allMatch(key->facts.getOrDefault(key,"").isBlank());
            }))answer.append("\n\nPhần giới thiệu hiện khá ngắn; mình chưa có đủ dữ liệu để kể chi tiết từng chương hay thêm tình tiết ngoài phần nội dung trên.");
        if(ChatBookFacts.has(owner.language.normalize(c.request().getMessage()),"muc luc|vi du|tung chuong") && c.state().explanationRound==2 && selected.stream().allMatch(b->owner.knowledge==null
                || owner.knowledge.approvedReadingFacts(b).keySet().stream().noneMatch(key->Set.of("audience","learningGoal","readingStyle").contains(key))))
            answer.append("\n\nMô tả hiện chưa có mục lục hoặc ví dụ cụ thể, nên mình chưa thể chỉ ra từng bài tập hay phương pháp trong sách. Bạn muốn áp dụng vào tình huống nào để mình giúp đối chiếu nhu cầu với phần nội dung đã biết?");
        String readerRequest=owner.language.normalize(Objects.toString(c.state().toolSearch.get("semanticQuery"),""));
        if(ChatBookFacts.has(readerRequest,"[0-9]+ tuoi") && selected.stream().anyMatch(b->owner.knowledge==null
                || owner.knowledge.approvedReadingFacts(b).getOrDefault("audience","").isBlank()))
            answer.append("\n\nCửa hàng chưa xác nhận độ tuổi đọc cho từng cuốn, nên mình chưa đủ căn cứ chọn cuốn hợp với bé nhất chỉ theo tuổi. Bạn có thể cân nhắc nội dung và xem thử cùng bé trước khi chọn; tuổi nhân vật không phải độ tuổi đọc của sách.");
        if("children".equals(c.state().toolSearch.get("readerGroup"))
                && ChatBookFacts.has(owner.language.normalize(c.request().getMessage()),"cuon nao.*(?:hop|sat|nhat)")) {
            List<String> comics=selected.stream().filter(b->ChatBookFacts.has(owner.language.normalize(owner.readingFacts(b).getOrDefault("summary","")),"truyen tranh"))
                .map(Product::getName).collect(Collectors.toList());
            if(!comics.isEmpty())answer.append("\n\nTheo mô tả, ").append(comics.stream().map(name->"“"+name+"”").collect(Collectors.joining(" và ")))
                .append(" là truyện tranh. Bé thường tự đọc truyện chữ hay quen đọc truyện tranh hơn?");
        }
        return new ChatTool.Result(answer.toString(),ChatIntent.PRODUCT_INFO,selected.stream().map(owner::dto).collect(Collectors.toList()),List.of(),"COMPLETED");
    }
    ChatTool.Result productFacts(List<Product> selected,String field,String question,ChatDialogueMemory.State state) {
        Set<String> fields=ChatBookFacts.requested(question,selected,owner.language);
        if(fields.isEmpty() && !ChatBookEditionQuestions.active(question)
                && !ChatBookFacts.has(owner.language.normalize(question),"muc luc"))fields.addAll(switch(field) {
            case "detail" -> List.of("author","price","inventory","description");
            case "bibliography" -> List.of("publisher","publicationYear","pageCount","isbn");
            default -> List.of(field);
        });
        Map<Long,Double> ratings=owner.data.ratingSnapshot();
        List<String> answers=new ArrayList<>();
        List<ChatActionDto> actions=new ArrayList<>();
        for(Product book:selected) {
            List<String> facts=new ArrayList<>();
            if(fields.contains("author"))facts.add("Tác giả: "+(book.getAuthor()==null || book.getAuthor().isBlank()?"chưa có dữ liệu":book.getAuthor()));
            if(fields.contains("price"))facts.add("Giá sau giảm: "+owner.money(owner.data.effectivePrice(book)));
            if(fields.contains("discount"))facts.add(book.getDiscount()>0?"Mức giảm: "+book.getDiscount()+"%.":"Mức giảm: 0%. Hiện không giảm giá.");
            if(fields.contains("inventory"))facts.add(book.getQuantity()>0?"Đang bán, còn "+book.getQuantity()+" bản theo tồn kho hiện tại.":"Cuốn này hiện hết hàng. Bạn có thể chọn Báo mình khi có hàng trên thẻ sách bên dưới; mình lưu thông báo vào tài khoản để bạn xem khi sách về nhé.");
            if(fields.contains("rating"))facts.add(ratings.containsKey(book.getProductId())?String.format(Locale.ROOT,"Rating: %.1f/5",ratings.get(book.getProductId())):"Chưa có đánh giá.");
            Map<String,String> reading=owner.readingFacts(book);
            List<String> details=ChatBookDetailQuestions.answers(question,reading,owner.language,book.getName());
            if(ChatBookFacts.has(owner.language.normalize(question),"muc luc"))
                details.add("Mình chưa có mục lục đã xác nhận của đúng ấn bản đang bán, nên chưa thể chép chính xác các chương. Bạn có thể xem phần giới thiệu hiện có hoặc nhờ cửa hàng kiểm tra ảnh mục lục.");
            if(fields.contains("description") && !fields.contains("format") && !fields.contains("communicationInstruction") && details.isEmpty())
                facts.add(ChatDialogueInterpreter.clean(owner.description(book),650));
            facts.addAll(details);
            facts.addAll(ChatBookFacts.directFacts(fields,reading,owner.language));
            facts.addAll(ChatBookFacts.studyFeatures(fields,reading,owner.language));
            facts.addAll(ChatBookEditionQuestions.answers(question));
            if(fields.contains("audience") && !reading.getOrDefault("audience","").isBlank())facts.add("Đối tượng đọc: "+reading.get("audience"));
            if(ChatConversationSupport.suitabilityQuestion(question,owner.language)) {
                if(!fields.contains("audience") && !reading.getOrDefault("audience","").isBlank())facts.add("Đối tượng đọc: "+reading.get("audience"));
                if(!reading.getOrDefault("readingLevel","").isBlank())facts.add("Mức đọc: "+reading.get("readingLevel"));
                if(ChatBookFacts.has(owner.language.normalize(question),"chua biet lap trinh")
                        && ChatBookFacts.has(owner.language.normalize(reading.getOrDefault("readingLevel","")),"can.*ma nguon"))
                    facts.add("Bạn chưa biết lập trình thì mình chưa chọn cuốn này làm điểm bắt đầu. Nên học kiến thức nền tảng và thực hành mã trước, để theo được các ví dụ mà hồ sơ sách đã xác nhận.");
            }
            if(ChatBookFacts.has(owner.language.normalize(question),"(?:hop|phu hop|danh cho|doc duoc).*(?:be|tre|tuoi)")) {
                Map<String,Object> ageRequest=new LinkedHashMap<>();
                ChatReadingFit readingFit=new ChatReadingFit(owner.language);readingFit.capture(ageRequest,question);
                if(readingFit.ageConflicts(ageRequest,owner.readingFacts(book)))facts.add(
                    "Tuổi bạn nêu nằm ngoài nhóm tuổi được nhà xuất bản phân loại trong hồ sơ. Vì vậy mình không xếp cuốn này vào gợi ý đúng độ tuổi bạn cần.");
            }
            if(new ChatReadingFit(owner.language).needs(state.toolSearch).contains("gentle")
                    && ChatBookFacts.has(owner.language.normalize(question),"thay vao|thay the|nen thay")
                    && ChatBookFacts.has(owner.language.normalize(reading.getOrDefault("summary","")+" "+reading.getOrDefault("readingStyle","")),"ket thuc.*buon"))
                facts.add("Với yêu cầu truyện nhẹ nhàng trước đó, mình không đề nghị thay cuốn này vào danh sách: phần giới thiệu đã xác nhận kết thúc buồn, nên hướng đọc này khác với sở thích bạn nêu.");
            Set<String> bibliography=new LinkedHashSet<>(fields);
            bibliography.retainAll(ChatBookFacts.BIBLIOGRAPHY);
            if(!bibliography.isEmpty())facts.add(owner.knowledge==null?ChatBookFacts.missingBibliography(bibliography):owner.knowledge.approvedBibliography(book,bibliography));
            answers.add("Về “"+book.getName()+(details.isEmpty()?"”, mình có những thông tin này:\n":"”:\n")+String.join("\n",facts));
            actions.add(new ChatActionDto("VIEW_PRODUCT","Xem sách",book.getProductId(),"/product/"+book.getProductId(),false));
            if(book.getQuantity()>0)actions.add(new ChatActionDto("ADD_TO_CART","Thêm giỏ",book.getProductId(),null,true));
        }
        return new ChatTool.Result(String.join("\n\n",answers),selected.size()==1?ChatIntent.PRODUCT_INFO:ChatIntent.COMPARE_BOOKS,
                selected.stream().map(owner::dto).collect(Collectors.toList()),actions,"COMPLETED");
    }
}

package vn.fs.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.fs.service.BookKnowledgeService;
import vn.fs.service.BookKnowledgeService.ProfileInput;

@RestController @RequestMapping("/api/admin/books")
public class BookKnowledgeApi {
    private final BookKnowledgeService knowledge;
    public BookKnowledgeApi(BookKnowledgeService knowledge) { this.knowledge=knowledge; }
    @PostMapping("/reindex") public java.util.Map<String,Object> reindex(){ return knowledge.reindexExisting(); }
    @GetMapping("/{id}/profile") public ProfileInput get(@PathVariable Long id) { return knowledge.get(id); }
    @PostMapping("/{id}/suggest") public ProfileInput suggest(@PathVariable Long id) { return knowledge.suggest(id); }
    @PutMapping("/{id}/publish") public ResponseEntity<ProfileInput> publish(@PathVariable Long id,@RequestBody ProfileInput input) {
        return ResponseEntity.ok(knowledge.publish(id,input));
    }
}

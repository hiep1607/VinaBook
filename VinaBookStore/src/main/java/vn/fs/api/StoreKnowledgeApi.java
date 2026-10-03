package vn.fs.api;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.fs.entity.StorePolicy;
import vn.fs.entity.StoreSettings;
import vn.fs.service.StoreKnowledgeService;

@RestController
public class StoreKnowledgeApi {
    private final StoreKnowledgeService knowledge;
    public StoreKnowledgeApi(StoreKnowledgeService knowledge){this.knowledge=knowledge;}
    @GetMapping("/api/store-knowledge") public Map<String,Object> publicKnowledge(){return knowledge.publicSnapshot();}
    @GetMapping("/api/admin/store-knowledge") public Map<String,Object> adminKnowledge(){return knowledge.adminSnapshot();}
    @PutMapping("/api/admin/store-knowledge/settings") public StoreSettings settings(@RequestBody StoreSettings input){return knowledge.updateSettings(input);}
    @PutMapping("/api/admin/store-knowledge/policies/{key}") public StorePolicy policy(@PathVariable String key,@RequestBody StorePolicy input){return knowledge.publishPolicy(key,input);}
}

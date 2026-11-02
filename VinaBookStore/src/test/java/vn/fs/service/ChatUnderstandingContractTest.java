package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import vn.fs.dto.chat.*;
import vn.fs.repository.ProductRepository;

class ChatUnderstandingContractTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ProductRepository products=mock(ProductRepository.class);
    final AiGateway ai=mock(AiGateway.class);
    ChatTurnUnderstanding understanding;
    ChatDialogueMemory.State state;
    @BeforeEach void setup()throws Exception {
        ChatIntentService language=new ChatIntentService();
        ChatBuiltinTools owner=new ChatBuiltinTools(products,new ChatRetrievalService(language),language,mock(ChatOrderSupportService.class),new ChatPolicyService(language));
        List<ChatTool> tools=new ArrayList<>();
        for(var method:ChatBuiltinTools.class.getDeclaredMethods())
            if(method.isAnnotationPresent(Bean.class)&&method.getReturnType()==ChatTool.class&&method.getParameterCount()==0)tools.add((ChatTool)method.invoke(owner));
        understanding=new ChatTurnUnderstanding(ai,mapper,new ChatToolRegistry(tools));
        state=new ChatDialogueMemory.State();state.contextVersion=3;state.initialized=true;
        state.focus=13L;state.displayed=new ArrayList<>(List.of(13L));state.shortlist=new ArrayList<>(state.displayed);
        state.toolSearch.put("query","phiêu lưu");state.toolSearch.put("maxPrice",100000);
    }
    ObjectNode plan() {
        ObjectNode root=mapper.createObjectNode().put("contextVersion",3).put("intent","PRODUCT_INFO").put("contextMode","current");
        root.set("set",mapper.createObjectNode());root.set("clear",mapper.createArrayNode());
        root.put("evidence",ChatTurnContext.CURRENT_MESSAGE);root.putNull("memoryNote");root.putNull("reply");
        root.set("calls",mapper.valueToTree(List.of(Map.of("tool","prepareCartChange","arguments",Map.of("reference","focus","operation","add","quantity",1)))));
        return root;
    }
    JsonNode validate(ObjectNode plan,String message)throws Exception {
        ChatRequestDto request=new ChatRequestDto();request.setMessage(message);
        return understanding.validate(mapper.writeValueAsString(plan),request,state,List.of());
    }
    @Test void repeatedConfirmedFiltersAreNotANewChangeOnACurrentBookAction()throws Exception {
        ObjectNode p=plan();p.with("set").put("query","phiêu lưu").put("maxPrice",100000);
        JsonNode accepted=validate(p,"lấy cho tôi cuốn này đi");
        assertThat(accepted.path("set").isEmpty()).isTrue();
        assertThat(accepted.path("calls").get(0).path("tool").asText()).isEqualTo("prepareCartChange");
        assertThat(state.toolSearch).containsEntry("query","phiêu lưu").containsEntry("maxPrice",100000);
        assertThat(state.contextVersion).isEqualTo(3);verifyNoInteractions(products);
    }
    @Test void aDifferentFilterStillRequiresAnExplicitRefine() {
        ObjectNode p=plan();p.with("set").put("maxPrice",50000);
        assertThatThrownBy(()->validate(p,"lấy cuốn này, dưới 50k nhé")).hasMessageContaining("Use refine");
        assertThat(state.toolSearch).containsEntry("maxPrice",100000);verifyNoInteractions(products);
    }
    @Test void anUnknownInternalFieldCannotBeSmuggledThroughAnEcho() {
        state.toolSearch.put("semanticQuery","");ObjectNode p=plan();p.with("set").put("semanticQuery","");
        assertThatThrownBy(()->validate(p,"lấy cho tôi cuốn này đi")).hasMessageContaining("Unknown context field");
        verifyNoInteractions(products);
    }
    @Test void bothReplyAndCartToolRemainRejectedBeforeAnyPreparation() {
        ObjectNode p=plan();p.put("reply","Đã thêm sách rồi.");
        assertThatThrownBy(()->validate(p,"lấy cho tôi cuốn này đi")).hasMessageContaining("Direct reply requires");
        verifyNoInteractions(products);
    }
    @Test void anotherToolsArgumentStillCannotBeUsedOnCartPreparation() {
        ObjectNode p=plan();((ObjectNode)p.path("calls").get(0).path("arguments")).put("field","detail");
        assertThatThrownBy(()->validate(p,"lấy cho tôi cuốn này đi")).hasMessageContaining("Unknown argument");
        verifyNoInteractions(products);
    }
    @Test void anInventedProductIdIsStillRejected() {
        ObjectNode p=plan();((ObjectNode)p.path("calls").get(0).path("arguments")).put("productId",999999);
        assertThatThrownBy(()->validate(p,"lấy cho tôi cuốn này đi")).hasMessageContaining("Product ID not grounded");
        verifyNoInteractions(products);
    }
    @Test void anInvalidCartPlanIsRepairedWithItsOwnArgumentsSchemaBeforeAnyExecution()throws Exception {
        ObjectNode invalid=plan();((ObjectNode)invalid.path("calls").get(0).path("arguments")).put("field","detail");
        when(ai.completeStructured(anyString(),anyList(),anyString(),any(JsonNode.class)))
            .thenReturn(mapper.writeValueAsString(invalid),mapper.writeValueAsString(plan()));
        ChatRequestDto request=new ChatRequestDto();request.setMessage("lấy cho tôi cuốn này đi");
        JsonNode result=understanding.understand(request,state,List.of());
        assertThat(result.path("calls").get(0).path("tool").asText()).isEqualTo("prepareCartChange");
        ArgumentCaptor<JsonNode> schemas=ArgumentCaptor.forClass(JsonNode.class);
        verify(ai,times(2)).completeStructured(anyString(),anyList(),anyString(),schemas.capture());
        JsonNode variants=schemas.getAllValues().get(1).path("anyOf").get(1).path("properties").path("calls").path("items").path("anyOf");
        assertThat(variants).hasSize(2);
        JsonNode cart=variants.get(0).path("properties");
        assertThat(cart.path("tool").path("enum").get(0).asText()).isEqualTo("prepareCartChange");
        assertThat(cart.path("arguments").path("properties").has("quantity")).isTrue();
        assertThat(cart.path("arguments").path("properties").has("field")).isFalse();
        verifyNoInteractions(products);
    }
    @Test void exportsTheRealToolSchemaAndExamplesForIndependentJsonSchemaValidation()throws Exception {
        Path directory=Path.of(System.getProperty("schema.contract.output","target/schema-contract"));Files.createDirectories(directory);
        JsonNode schema=understanding.repairSchema(mapper.writeValueAsString(plan()));
        Files.writeString(directory.resolve("schema.json"),mapper.writeValueAsString(schema));
        Files.writeString(directory.resolve("primary-schema.json"),mapper.writeValueAsString(understanding.schema()));
        ObjectNode cart=plan(),conversation=plan();cart.remove("reply");conversation.put("reply","Mình có thể gợi mở vài hướng đọc.");conversation.set("calls",mapper.createArrayNode());
        ObjectNode both=cart.deepCopy();both.put("reply","Đã thêm sách.");
        ObjectNode blank=cart.deepCopy();blank.put("reply","");
        ObjectNode wrongArg=cart.deepCopy();((ObjectNode)wrongArg.path("calls").get(0).path("arguments")).put("field","detail");
        ObjectNode wrongCount=cart.deepCopy();((ObjectNode)wrongCount.path("calls").get(0).path("arguments")).put("count",1);
        ObjectNode wrongOperation=cart.deepCopy();((ObjectNode)wrongOperation.path("calls").get(0).path("arguments")).put("operation","delete_everything");
        ObjectNode unknown=cart.deepCopy();((ObjectNode)unknown.path("calls").get(0)).put("tool","adminDeleteCatalog");
        ObjectNode read=cart.deepCopy();read.set("calls",mapper.valueToTree(List.of(Map.of("tool","getProducts","arguments",Map.of("reference","focus","field","price")))));
        Files.writeString(directory.resolve("examples.json"),mapper.writeValueAsString(List.of(
            Map.of("name","cart_preparation","valid",true,"plan",cart),Map.of("name","plain_conversation","valid",true,"plan",conversation),
            Map.of("name","reply_and_tool","valid",false,"plan",both),Map.of("name","empty_reply_and_tool","valid",false,"plan",blank),
            Map.of("name","wrong_tool_field","valid",false,"plan",wrongArg),Map.of("name","wrong_tool_count","valid",false,"plan",wrongCount),
            Map.of("name","invalid_operation","valid",false,"plan",wrongOperation),Map.of("name","unknown_tool","valid",false,"plan",unknown),
            Map.of("name","read_price","valid",true,"plan",read))));
        Files.writeString(directory.resolve("primary-examples.json"),mapper.writeValueAsString(List.of(
            Map.of("name","cart_preparation","valid",true,"plan",cart),Map.of("name","plain_conversation","valid",true,"plan",conversation),
            Map.of("name","reply_and_tool","valid",false,"plan",both),Map.of("name","empty_reply_and_tool","valid",false,"plan",blank),
            Map.of("name","unknown_tool","valid",false,"plan",unknown))));
        assertThat(schema.path("anyOf")).hasSize(2);
        for(JsonNode branch:schema.path("anyOf"))assertThat(branch.path("required").toString()).contains("calls");
        verifyNoInteractions(products);
    }
}

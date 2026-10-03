package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.fs.config.AiSettings;

class BookEmbeddingGatewayTest {
    @Test void repeatedQueryReusesVectorButDocumentsAndDifferentModelsRemainSeparate() {
        AiSettings settings=new AiSettings();
        class Fixture extends BookEmbeddingGateway {
            int requests;
            Fixture(){super(settings,new ObjectMapper());}
            @Override protected List<Double> embed(String text,String task,String title){requests++;return List.of(1.0,0.0);}
        }
        Fixture gateway=new Fixture();
        gateway.query("  Giảm   xao nhãng ");gateway.query("giảm xao nhãng");
        assertThat(gateway.requests).isEqualTo(1);
        gateway.document("Tên sách","giảm xao nhãng");
        assertThat(gateway.requests).isEqualTo(2);
        settings.setEmbeddingModel("another-model");gateway.query("giảm xao nhãng");
        assertThat(gateway.requests).isEqualTo(3);
    }
    @Test void failedQueryDoesNotBecomeACachedEmptyAnswer() {
        class Fixture extends BookEmbeddingGateway {
            int requests;
            Fixture(){super(new AiSettings(),new ObjectMapper());}
            @Override protected List<Double> embed(String text,String task,String title){
                if(++requests==1)throw new IllegalStateException("temporary");return List.of(1.0,0.0);
            }
        }
        Fixture gateway=new Fixture();
        assertThatThrownBy(()->gateway.query("mục tiêu đọc")).isInstanceOf(IllegalStateException.class);
        assertThat(gateway.query("mục tiêu đọc")).containsExactly(1.0,0.0);
        assertThat(gateway.requests).isEqualTo(2);
    }
}

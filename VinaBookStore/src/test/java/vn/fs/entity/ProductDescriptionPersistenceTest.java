package vn.fs.entity;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","spring.datasource.url=jdbc:h2:mem:product_description;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"},showSql=false)
class ProductDescriptionPersistenceTest {
    @Autowired TestEntityManager entities;

    @Test void preservesRichDescriptionsLongerThan255Characters() {
        String description="<p>Một mô tả đầy đủ về cuốn sách và nội dung dành cho người đọc.</p>".repeat(60);
        Product product=new Product();
        product.setName("Sách có mô tả chi tiết");
        product.setPrice(120000d);
        product.setStatus(true);
        product.setDescription(description);
        Product saved=entities.persistAndFlush(product);
        entities.clear();
        assertThat(entities.find(Product.class,saved.getProductId()).getDescription()).isEqualTo(description);
    }
}

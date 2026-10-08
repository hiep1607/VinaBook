package vn.fs.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

class AdminCatalogCorsTest {
    private MockMvc mvc;
    private ProductRepository products;
    private AnnotationConfigWebApplicationContext context;

    @Configuration @EnableWebMvc
    static class MvcConfig {
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean AdminCatalogApi catalog(ProductRepository products) { return new AdminCatalogApi(products); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcConfig.class);
        context.refresh();
        products = context.getBean(ProductRepository.class);
        when(products.findAll()).thenReturn(Collections.emptyList());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach void closeContext() { context.close(); }

    @Test void acceptsLocalhostAdminPreflightWithBearerHeader() throws Exception {
        mvc.perform(options("/api/admin/products").header("Origin", "http://localhost:4300")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"))
            .andExpect(header().string("Access-Control-Allow-Headers", "authorization"));
        verifyNoInteractions(products);
    }

    @Test void acceptsLoopbackAdminOrigin() throws Exception {
        mvc.perform(options("/api/admin/products").header("Origin", "http://127.0.0.1:4300")
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4300"));
    }

    @Test void rejectsUnrecognizedOriginWithoutQueryingCatalog() throws Exception {
        mvc.perform(options("/api/admin/products").header("Origin", "https://unexpected.example")
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(products);
    }

    @Test void returnsCorsHeaderOnCatalogResponse() throws Exception {
        mvc.perform(get("/api/admin/products").header("Origin", "http://localhost:4300"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"))
            .andExpect(content().json("[]"));
    }

    @Test void appliesCorsToProductDetailToo() throws Exception {
        Product product = new Product(); product.setProductId(3L); product.setName("Test book");
        when(products.findById(3L)).thenReturn(Optional.of(product));
        mvc.perform(get("/api/admin/products/3").header("Origin", "http://localhost:4300"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"))
            .andExpect(jsonPath("$.productId").value(3));
    }
}

package vn.fs.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.fs.entity.Product;
import vn.fs.repository.ProductRepository;

@RestController @RequestMapping("/api/admin/products")
public class AdminCatalogApi {
    private final ProductRepository products;
    public AdminCatalogApi(ProductRepository products) { this.products=products; }
    @GetMapping public List<Product> all() { return products.findAll(); }
    @GetMapping("/{id}") public Product one(@PathVariable Long id) {
        return products.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));
    }
}

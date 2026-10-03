/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import vn.fs.entity.Category;
import vn.fs.entity.Product;
import vn.fs.repository.CategoryRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.service.BookKnowledgeService;

@CrossOrigin("*")
@RestController
@RequestMapping("api/products")
public class ProductApi {

	@Autowired
	ProductRepository repo;

	@Autowired
	CategoryRepository cRepo;
	@Autowired
	BookKnowledgeService knowledge;

	@GetMapping
	public ResponseEntity<List<Product>> getAll() {
		return ResponseEntity.ok(repo.findByStatusTrue());
	}

	@GetMapping("bestseller")
	public ResponseEntity<List<Product>> getBestSeller() {
		return ResponseEntity.ok(repo.findByStatusTrueOrderBySoldDesc());
	}

	@GetMapping("bestseller-admin")
	public ResponseEntity<List<Product>> getBestSellerAdmin() {
		return ResponseEntity.ok(repo.findTop10ByOrderBySoldDesc());
	}

	@GetMapping("latest")
	public ResponseEntity<List<Product>> getLasted() {
		return ResponseEntity.ok(repo.findByStatusTrueOrderByEnteredDateDesc());
	}

	@GetMapping("rated")
	public ResponseEntity<List<Product>> getRated() {
		return ResponseEntity.ok(repo.findProductRated());
	}

	@GetMapping("suggest/{categoryId}/{productId}")
	public ResponseEntity<List<Product>> suggest(@PathVariable("categoryId") Long categoryId,
			@PathVariable("productId") Long productId) {
		return ResponseEntity.ok(repo.findProductSuggest(categoryId, productId, categoryId, categoryId));
	}

	@GetMapping("category/{id}")
	public ResponseEntity<List<Product>> getByCategory(@PathVariable("id") Long id) {
		if (!cRepo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Category c = cRepo.findById(id).get();
		return ResponseEntity.ok(repo.findByCategoryAndStatusTrue(c));
	}

	@GetMapping("{id}")
	public ResponseEntity<Product> getById(@PathVariable("id") Long id) {
		if (repo.findByProductIdAndStatusTrue(id) == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(repo.findByProductIdAndStatusTrue(id));
	}

	@PostMapping
	public ResponseEntity<Product> post(@RequestBody Product product) {
		if (repo.existsById(product.getProductId())) {
			return ResponseEntity.badRequest().build();
		}
		if (product.getPreviewEnabled() == null) {
			product.setPreviewEnabled(Boolean.FALSE);
		}
		if (product.getPreviewRightsConfirmed() == null) {
			product.setPreviewRightsConfirmed(Boolean.FALSE);
		}
		// New books are drafts until the reviewed knowledge profile is searchable.
		product.setStatus(false);
		return ResponseEntity.ok(repo.save(product));
	}

	@PutMapping("{id}")
	@Transactional
	public ResponseEntity<Product> put(@PathVariable("id") Long id, @RequestBody Product product) {
		if (!id.equals(product.getProductId())) {
			return ResponseEntity.badRequest().build();
		}
		if (!repo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Product stored = repo.findById(id).get();
		boolean editorialChange = !Objects.equals(stored.getName(),product.getName())
				|| !Objects.equals(stored.getAuthor(),product.getAuthor())
				|| !Objects.equals(stored.getDescription(),product.getDescription())
				|| !Objects.equals(stored.getCategory()==null?null:stored.getCategory().getCategoryId(),
						product.getCategory()==null?null:product.getCategory().getCategoryId());
		product.setPreviewEnabled(product.getPreviewEnabled() == null ? stored.getPreviewEnabled() : product.getPreviewEnabled());
		product.setPreviewRightsConfirmed(product.getPreviewRightsConfirmed() == null
				? stored.getPreviewRightsConfirmed() : product.getPreviewRightsConfirmed());
		// Publication is reserved for the reviewed book profile endpoint.
		product.setStatus(editorialChange ? false : stored.getStatus());
		Product saved = repo.save(product);
		if (editorialChange) knowledge.invalidateProfile(id);
		return ResponseEntity.ok(saved);
	}

	@DeleteMapping("{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
		if (!repo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Product p = repo.findById(id).get();
		p.setStatus(false);
		repo.save(p);
		return ResponseEntity.ok().build();
	}

}

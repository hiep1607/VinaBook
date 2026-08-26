/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.fs.entity.Favorite;
import vn.fs.entity.Product;
import vn.fs.entity.User;
import vn.fs.repository.FavoriteRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.repository.UserRepository;
import vn.fs.config.CurrentUserAccess;

@CrossOrigin("*")
@RestController
@RequestMapping("api/favorites")
public class FavoritesApi {

	@Autowired
	FavoriteRepository favoriteRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	CurrentUserAccess access;

	@GetMapping("email/{email}")
	public ResponseEntity<List<Favorite>> findByEmail(@PathVariable("email") String email) {
		if (!access.ownsEmail(email)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (userRepository.existsByEmail(email)) {
			return ResponseEntity.ok(favoriteRepository.findByUser(userRepository.findByEmail(email).get()));
		}
		return ResponseEntity.notFound().build();
	}

	@GetMapping("product/{id}")
	public ResponseEntity<Integer> findByProduct(@PathVariable("id") Long id) {
		if (productRepository.existsById(id)) {
			return ResponseEntity.ok(favoriteRepository.countByProduct(productRepository.getById(id)));
		}
		return ResponseEntity.notFound().build();
	}

	@GetMapping("{productId}/{email}")
	public ResponseEntity<Favorite> findByProductAndUser(@PathVariable("productId") Long productId,
			@PathVariable("email") String email) {
		if (!access.ownsEmail(email)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (userRepository.existsByEmail(email)) {
			if (productRepository.existsById(productId)) {
				Product product = productRepository.findById(productId).get();
				User user = userRepository.findByEmail(email).get();
				return ResponseEntity.ok(favoriteRepository.findByProductAndUser(product, user));
			}
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("email")
	public ResponseEntity<Favorite> post(@RequestBody Favorite favorite) {
		if (favorite.getUser() == null || favorite.getProduct() == null
				|| favorite.getUser().getUserId() == null || favorite.getProduct().getProductId() == null) {
			return ResponseEntity.badRequest().build();
		}
		if (!access.ownsUserId(favorite.getUser().getUserId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (!userRepository.existsById(favorite.getUser().getUserId())
				|| !productRepository.existsById(favorite.getProduct().getProductId())) {
			return ResponseEntity.notFound().build();
		}
		User user = userRepository.findById(favorite.getUser().getUserId()).get();
		Product product = productRepository.findById(favorite.getProduct().getProductId()).get();
		Favorite existing = favoriteRepository.findByProductAndUser(product, user);
		if (existing != null) {
			return ResponseEntity.ok(existing);
		}
		favorite.setFavoriteId(null);
		favorite.setUser(user);
		favorite.setProduct(product);
		return ResponseEntity.ok(favoriteRepository.save(favorite));
	}

	@DeleteMapping("{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
		if (favoriteRepository.existsById(id)) {
			Favorite favorite = favoriteRepository.findById(id).get();
			if (favorite.getUser() == null || !access.ownsUserId(favorite.getUser().getUserId())) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}
			favoriteRepository.deleteById(id);
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.notFound().build();
	}

}

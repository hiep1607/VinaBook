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

@CrossOrigin("*")
@RestController
@RequestMapping("api/favorites")
public class FavoritesApi {
    @Autowired vn.fs.config.CurrentUserAccess access;
    @Autowired vn.fs.automation.AutomationService automation;
    @javax.persistence.PersistenceContext javax.persistence.EntityManager em;

	@Autowired
	FavoriteRepository favoriteRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductRepository productRepository;

	@GetMapping("email/{email}")
	public ResponseEntity<List<Favorite>> findByEmail(@PathVariable("email") String email) {
        if(!access.canAccessEmail(email))return ResponseEntity.status(403).build();
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
        if(!access.canAccessEmail(email))return ResponseEntity.status(403).build();
		if (userRepository.existsByEmail(email)) {
			if (productRepository.existsById(productId)) {
				Product product = productRepository.findById(productId).get();
				User user = userRepository.findByEmail(email).get();
				return ResponseEntity.ok(favoriteRepository.findByProductAndUser(product, user));
			}
		}
		return ResponseEntity.notFound().build();
	}

	@org.springframework.transaction.annotation.Transactional
    @PostMapping("email")
	public ResponseEntity<Favorite> post(@RequestBody Favorite favorite) {
        if(access.currentUserId()==null)return ResponseEntity.status(401).build();
        User user=em.find(User.class,access.currentUserId(),javax.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(user==null || favorite.getProduct()==null || favorite.getProduct().getProductId()==null)return ResponseEntity.badRequest().build();
        Product product=productRepository.findById(favorite.getProduct().getProductId()).orElse(null);
        if(product==null)return ResponseEntity.notFound().build();
        Favorite existing=favoriteRepository.findByProductAndUser(product,user);
        if(existing!=null)return ResponseEntity.ok(existing);
        favorite.setFavoriteId(null);favorite.setUser(user);favorite.setProduct(product);
        Favorite saved=favoriteRepository.saveAndFlush(favorite);
        automation.trackFavorite(saved);
        return ResponseEntity.ok(saved);
	}

	@DeleteMapping("{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        Favorite stored=favoriteRepository.findById(id).orElse(null);
        if(stored==null || stored.getUser()==null || !access.ownsUserId(stored.getUser().getUserId()))return ResponseEntity.notFound().build();
		if (favoriteRepository.existsById(id)) {
			favoriteRepository.deleteById(id);
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.notFound().build();
	}

}

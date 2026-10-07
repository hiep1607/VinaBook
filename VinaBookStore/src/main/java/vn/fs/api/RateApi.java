/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import java.util.List;
import java.util.Date;
import java.util.Objects;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.fs.entity.Rate;
import vn.fs.entity.User;
import vn.fs.entity.OrderDetail;
import vn.fs.repository.OrderDetailRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.repository.RateRepository;
import vn.fs.config.CurrentUserAccess;

@CrossOrigin("*")
@RestController
@RequestMapping("api/rates")
public class RateApi {

	@Autowired
	RateRepository rateRepository;

	@Autowired
	OrderDetailRepository orderDetailRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	CurrentUserAccess access;

	@PersistenceContext
	EntityManager em;

	@GetMapping
	public ResponseEntity<List<Rate>> findAll() {
		return ResponseEntity.ok(rateRepository.findAllByOrderByIdDesc());
	}

	@GetMapping("{orderDetailId}")
	public ResponseEntity<Rate> findById(@PathVariable Long orderDetailId) {
		if (!orderDetailRepository.existsById(orderDetailId)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(rateRepository.findByOrderDetail(orderDetailRepository.findById(orderDetailId).get()));
	}

	@GetMapping("/product/{id}")
	public ResponseEntity<List<Rate>> findByProduct(@PathVariable("id") Long id) {
		if (!productRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(rateRepository.findByProductOrderByIdDesc(productRepository.findById(id).get()));
	}

	@PostMapping
	@Transactional
	public ResponseEntity<Rate> post(@RequestBody Rate rate) {
		if (rate == null || rate.getId() != null || rate.getUser() == null || rate.getUser().getUserId() == null
				|| rate.getProduct() == null || rate.getProduct().getProductId() == null || !validRating(rate)) {
			return ResponseEntity.badRequest().build();
		}
		Long ownerId = access.currentUserId();
		if (ownerId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!Objects.equals(ownerId, rate.getUser().getUserId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		// Lock the JWT owner even when no rate exists yet, so concurrent POSTs serialize.
		User user = em.find(User.class, ownerId, LockModeType.PESSIMISTIC_WRITE);
		if (user == null) return ResponseEntity.notFound().build();
		var product = productRepository.findById(rate.getProduct().getProductId()).orElse(null);
		if (product == null) return ResponseEntity.notFound().build();
		// orderDetail là tùy chọn — storefront cho phép đánh giá không cần mua hàng
		OrderDetail detail = null;
		if (rate.getOrderDetail() != null) {
			if (rate.getOrderDetail().getOrderDetailId() == null) return ResponseEntity.badRequest().build();
			detail = orderDetailRepository.findById(rate.getOrderDetail().getOrderDetailId()).orElse(null);
			if (detail == null) return ResponseEntity.notFound().build();
			if (detail.getOrder() == null || detail.getOrder().getUser() == null
					|| !Objects.equals(ownerId, detail.getOrder().getUser().getUserId()))
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			if (detail.getProduct() == null || !Objects.equals(product.getProductId(), detail.getProduct().getProductId()))
				return ResponseEntity.badRequest().build();
		}
		Rate stored = rateRepository.findFirstByUser_UserIdAndProduct_ProductIdOrderByIdDesc(ownerId, product.getProductId());
		if (stored == null) {
			stored = new Rate();
			stored.setUser(user);
			stored.setProduct(product);
			stored.setOrderDetail(detail);
		}
		stored.setRating(rate.getRating());
		stored.setComment(rate.getComment().trim());
		stored.setRateDate(new Date());
		return ResponseEntity.ok(rateRepository.save(stored));
	}

	@PutMapping
	@Transactional
	public ResponseEntity<Rate> put(@RequestBody Rate rate) {
		if (rate == null || rate.getId() == null) {
			return ResponseEntity.badRequest().build();
		}
		Long ownerId = access.currentUserId();
		if (ownerId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		if (em.find(User.class, ownerId, LockModeType.PESSIMISTIC_WRITE) == null) return ResponseEntity.notFound().build();
		Rate stored = rateRepository.findById(rate.getId()).orElse(null);
		if (stored == null) return ResponseEntity.notFound().build();
		if (stored.getUser() == null || !Objects.equals(ownerId, stored.getUser().getUserId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (rate.getProduct() != null && (stored.getProduct() == null
				|| !Objects.equals(stored.getProduct().getProductId(), rate.getProduct().getProductId())))
			return ResponseEntity.badRequest().build();
		if (!validRating(rate)) {
			return ResponseEntity.badRequest().build();
		}
		stored.setRating(rate.getRating());
		stored.setComment(rate.getComment().trim());
		stored.setRateDate(new Date());
		return ResponseEntity.ok(rateRepository.save(stored));
	}

	@DeleteMapping("{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
		if (!rateRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		rateRepository.deleteById(id);
		return ResponseEntity.ok().build();
	}

	private boolean validRating(Rate rate) {
		return rate.getRating() != null && rate.getRating() >= 1 && rate.getRating() <= 5
				&& rate.getRating() == Math.rint(rate.getRating())
				&& rate.getComment() != null && !rate.getComment().trim().isEmpty()
				&& rate.getComment().trim().length() <= 2000;
	}

}

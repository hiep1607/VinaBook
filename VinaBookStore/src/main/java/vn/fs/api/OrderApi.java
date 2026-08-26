/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import vn.fs.entity.Cart;
import vn.fs.entity.CartDetail;
import vn.fs.entity.Order;
import vn.fs.entity.OrderDetail;
import vn.fs.entity.Product;
import vn.fs.repository.CartDetailRepository;
import vn.fs.repository.CartRepository;
import vn.fs.repository.OrderDetailRepository;
import vn.fs.repository.OrderRepository;
import vn.fs.repository.ProductRepository;
import vn.fs.repository.UserRepository;
import vn.fs.utils.SendMailUtil;
import vn.fs.config.CurrentUserAccess;

@CrossOrigin("*")
@RestController
@RequestMapping("api/orders")
public class OrderApi {

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	OrderDetailRepository orderDetailRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	CartRepository cartRepository;

	@Autowired
	CartDetailRepository cartDetailRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	SendMailUtil senMail;

	@Autowired
	CurrentUserAccess access;

	@GetMapping
	public ResponseEntity<List<Order>> findAll() {
		return ResponseEntity.ok(orderRepository.findAllByOrderByOrdersIdDesc());
	}

	@GetMapping("{id}")
	public ResponseEntity<Order> getById(@PathVariable("id") Long id) {
		if (!orderRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(orderRepository.findById(id).get());
	}

	@GetMapping("/user/{email}")
	public ResponseEntity<List<Order>> getByUser(@PathVariable("email") String email) {
		if (!access.canAccessEmail(email)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (!userRepository.existsByEmail(email)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity
				.ok(orderRepository.findByUserOrderByOrdersIdDesc(userRepository.findByEmail(email).get()));
	}

	@PostMapping("/{email}")
	@Transactional
	public ResponseEntity<Order> checkout(@PathVariable("email") String email, @RequestBody Cart cart,
			@RequestParam(value = "paymentMethod", defaultValue = "COD") String paymentMethod) {
		if (!access.ownsEmail(email)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (!userRepository.existsByEmail(email)) {
			return ResponseEntity.notFound().build();
		}
		if (!cartRepository.existsById(cart.getCartId())) {
			return ResponseEntity.notFound().build();
		}
		Cart storedCart = cartRepository.findById(cart.getCartId()).get();
		if (storedCart.getUser() == null || !access.ownsUserId(storedCart.getUser().getUserId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<CartDetail> items = cartDetailRepository.findByCart(storedCart);
		if (items.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		Double amount = 0.0;
		for (CartDetail i : items) {
			if (i.getQuantity() <= 0 || i.getProduct() == null) {
				return ResponseEntity.badRequest().build();
			}
			Product product = productRepository.findByProductIdAndStatusTrue(i.getProduct().getProductId());
			if (product == null || i.getQuantity() > product.getQuantity()) {
				return ResponseEntity.badRequest().build();
			}
			i.setProduct(product);
			i.setPrice(lineTotal(product, i.getQuantity()));
			amount += i.getPrice();
		}
		if (amount > 0 && amount < 300000) {
			amount += 20000;
		}
		String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
		if (normalizedPaymentMethod == null) {
			return ResponseEntity.badRequest().build();
		}
		Order order = new Order();
		order.setOrderDate(new Date());
		order.setAmount(amount);
		order.setAddress(storedCart.getAddress());
		order.setPhone(storedCart.getPhone());
		order.setStatus(0);
		order.setPaymentMethod(normalizedPaymentMethod);
		order.setPaymentStatus("COD".equals(normalizedPaymentMethod) ? "UNPAID" : "PENDING");
		order.setUser(userRepository.findByEmail(email).get());
		order = orderRepository.save(order);
		for (CartDetail i : items) {
			OrderDetail orderDetail = new OrderDetail(0L, i.getQuantity(), i.getPrice(), i.getProduct(), order);
			orderDetailRepository.save(orderDetail);
		}
		for (CartDetail i : items) {
			cartDetailRepository.delete(i);
		}
		senMail.sendMailOrder(order);
		return ResponseEntity.ok(order);
	}

	@PutMapping("cancel/{orderId}")
	@Transactional
	public ResponseEntity<Void> cancel(@PathVariable("orderId") Long id) {
		if (!orderRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Order order = orderRepository.findById(id).get();
		if (order.getStatus() != 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		if ("PAID".equals(order.getPaymentStatus()) && !"COD".equals(order.getPaymentMethod())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		order.setStatus(3);
		orderRepository.save(order);
		senMail.sendMailOrderCancel(order);
		return ResponseEntity.ok().build();
	}

	@PutMapping("deliver/{orderId}")
	@Transactional
	public ResponseEntity<Void> deliver(@PathVariable("orderId") Long id) {
		if (!orderRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Order order = orderRepository.findById(id).get();
		if (order.getStatus() != 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		if (!"COD".equals(paymentMethod(order)) && !"PAID".equals(paymentStatus(order))) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		order.setStatus(1);
		orderRepository.save(order);
		senMail.sendMailOrderDeliver(order);
		return ResponseEntity.ok().build();
	}

	@PutMapping("success/{orderId}")
	@Transactional
	public ResponseEntity<Void> success(@PathVariable("orderId") Long id) {
		if (!orderRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		Order order = orderRepository.findById(id).get();
		if (order.getStatus() == 2) {
			return ResponseEntity.ok().build();
		}
		if (order.getStatus() != 1) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		if (!"COD".equals(paymentMethod(order)) && !"PAID".equals(paymentStatus(order))) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		if (!hasEnoughStock(order)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		order.setStatus(2);
		if ("COD".equals(paymentMethod(order))) {
			order.setPaymentStatus("PAID");
			order.setPaidAt(new Date());
		}
		orderRepository.save(order);
		senMail.sendMailOrderSuccess(order);
		updateProduct(order);
		return ResponseEntity.ok().build();
	}

	private String normalizePaymentMethod(String value) {
		if (value == null) {
			return "COD";
		}
		String normalized = value.trim().toUpperCase();
		return normalized.matches("COD|BANK_QR|VNPAY|MOMO") ? normalized : null;
	}

	private String paymentMethod(Order order) {
		return order.getPaymentMethod() == null ? "COD" : order.getPaymentMethod();
	}

	private String paymentStatus(Order order) {
		return order.getPaymentStatus() == null ? "UNPAID" : order.getPaymentStatus();
	}

	public void updateProduct(Order order) {
		List<OrderDetail> listOrderDetail = orderDetailRepository.findByOrder(order);
		for (OrderDetail orderDetail : listOrderDetail) {
			Product product = productRepository.findById(orderDetail.getProduct().getProductId()).get();
			if (product != null) {
				product.setQuantity(product.getQuantity() - orderDetail.getQuantity());
				product.setSold(product.getSold() + orderDetail.getQuantity());
				productRepository.save(product);
			}
		}
	}

	private boolean hasEnoughStock(Order order) {
		for (OrderDetail detail : orderDetailRepository.findByOrder(order)) {
			Product product = productRepository.findByProductIdAndStatusTrue(detail.getProduct().getProductId());
			if (product == null || detail.getQuantity() <= 0 || product.getQuantity() < detail.getQuantity()) {
				return false;
			}
		}
		return true;
	}

	private Double lineTotal(Product product, int quantity) {
		double unit = Math.round(product.getPrice() * (100 - product.getDiscount()) / 100);
		return unit * quantity;
	}

}

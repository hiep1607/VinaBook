/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.fs.entity.Order;
import vn.fs.entity.User;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.user.userId = :owner and (:status < 0 or o.status = :status) and (:term = '' or str(o.ordersId) like :pattern or exists (select d.orderDetailId from OrderDetail d where d.order = o and lower(function('replace', d.product.name, 'đ', 'd')) like :pattern))")
    org.springframework.data.domain.Page<Order> findCustomerPage(@org.springframework.data.repository.query.Param("owner") Long owner,
        @org.springframework.data.repository.query.Param("status") int status, @org.springframework.data.repository.query.Param("term") String term,
        @org.springframework.data.repository.query.Param("pattern") String pattern, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select o.status, count(o), sum(o.amount) from Order o where o.user.userId = :owner group by o.status")
    List<Object[]> customerCounts(@org.springframework.data.repository.query.Param("owner") Long owner);

	List<Order> findByUser(User user);

	List<Order> findByUserOrderByOrdersIdDesc(User user);

	List<Order> findAllByOrderByOrdersIdDesc();

	List<Order> findByStatus(int status);

	Optional<Order> findByPaymentReference(String paymentReference);

}

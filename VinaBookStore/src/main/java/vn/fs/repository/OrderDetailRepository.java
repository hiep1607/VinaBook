/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.fs.entity.Order;
import vn.fs.entity.OrderDetail;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    @org.springframework.data.jpa.repository.Query("select d from OrderDetail d left join fetch d.product where d.order.ordersId in :ids")
    List<OrderDetail> findCustomerPageItems(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

	List<OrderDetail> findByOrder(Order order);

}

/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order implements Serializable {
	@org.hibernate.annotations.ColumnDefault("false")
	@javax.persistence.Column(nullable = false)
	private boolean stockReserved;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long ordersId;
	private Date orderDate;
	private Double amount;
	@javax.persistence.Column(length=400)
	private String address;
	private String phone;
	private int status;
	private String paymentMethod;
	private String paymentStatus;
	private String paymentReference;
	private String paymentTransactionId;
	private Date paidAt;
	private String paymentGatewayDate;
	@javax.persistence.Column(length = 2048)
	private String paymentCheckoutUrl;
    private String voucherCode;
    private Long voucherDiscount;
    private Long shippingFee;

	@ManyToOne
	@JoinColumn(name = "userId")
	private User user;

}

package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShippingDetailsTest {
    @Test void savedAndCheckoutDeliveryAcceptSameLongAddressAndPhoneFormat() {
        var delivery=ShippingDetails.require("Người nhận","+84 912-345-678","A".repeat(255));
        assertThat(delivery.phone()).isEqualTo("0912345678");assertThat(delivery.street()).hasSize(255);
        assertThatThrownBy(()->ShippingDetails.require("Người nhận","0912345678","A".repeat(256))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}

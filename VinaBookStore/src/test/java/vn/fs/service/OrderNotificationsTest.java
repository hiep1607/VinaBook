package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.handler.NotificationWebSocketHandle;
import vn.fs.repository.NotificationRepository;

class OrderNotificationsTest {
    @Test void notificationDatabaseFailureDoesNotFailAnAlreadyCommittedOrder() {
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var rows=mock(NotificationRepository.class);when(rows.saveAndFlush(any())).thenThrow(new IllegalStateException("offline"));
        var sockets=mock(NotificationWebSocketHandle.class);
        var notifications=new OrderNotifications(mock(ApplicationEventPublisher.class),sockets,new ObjectMapper(),rows,manager);
        assertThatCode(()->notifications.publish(new OrderNotifications.Changed(12L,"Đơn thử"))).doesNotThrowAnyException();
        verify(manager).rollback(any());verifyNoInteractions(sockets);
    }
}

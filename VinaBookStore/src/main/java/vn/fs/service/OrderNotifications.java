package vn.fs.service;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import vn.fs.handler.NotificationWebSocketHandle;

@Service
public class OrderNotifications {
    public record Changed(Long orderId,String message) {}
    private final ApplicationEventPublisher events;
    private final NotificationWebSocketHandle sockets;
    private final ObjectMapper mapper;
    private final vn.fs.repository.NotificationRepository notifications;
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(OrderNotifications.class);
    public OrderNotifications(ApplicationEventPublisher events,NotificationWebSocketHandle sockets,ObjectMapper mapper,vn.fs.repository.NotificationRepository notifications,org.springframework.transaction.PlatformTransactionManager manager){
        this.events=events;this.sockets=sockets;this.mapper=mapper;this.notifications=notifications;
        transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void changed(Long id,String message){events.publishEvent(new Changed(id,message));}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void publish(Changed event) {
        try {
            transaction.executeWithoutResult(status->{
                var notification=new vn.fs.entity.Notification();notification.setMessage(event.message());
                notification.setTime(new java.util.Date());notification.setStatus(false);notifications.saveAndFlush(notification);
            });
            sockets.publish(mapper.writeValueAsString(Map.of("type","ORDER_CHANGED","orderId",event.orderId(),"user","VinaBook","message",event.message())));
        }catch(Exception unavailable){
            // The order has already committed. Notification failure must not report checkout failure.
            log.warn("Order notification unavailable for order {} ({})",event.orderId(),unavailable.getClass().getSimpleName());
        }
    }
}

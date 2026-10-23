package vn.fs.automation;

import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import vn.fs.service.OrderNotifications;

@Component
public class AutomationRunner {
    private final AutomationService service;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AutomationRunner.class);
    public AutomationRunner(AutomationService service){this.service=service;}
    @EventListener(ApplicationReadyEvent.class) public void ready(){service.initialize();}
    @Scheduled(fixedDelayString="${automation.scan-delay-ms:60000}",initialDelayString="${automation.initial-delay-ms:60000}") public void scheduled(){run(true);}
    public void manual(){service.requireAdmin();run(false);}
    private void run(boolean email){Instant start=Instant.now();try{int count=service.scan(email);service.recordRun(start,"SUCCESS",count);}catch(RuntimeException failed){service.recordRun(start,"FAILED",0);log.warn("Automation scan failed ({})",failed.getClass().getSimpleName());}}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true) public void orderChanged(OrderNotifications.Changed change){try{service.orderChanged(change.orderId());}catch(RuntimeException failed){log.warn("Customer order notification delayed for order {} ({})",change.orderId(),failed.getClass().getSimpleName());}}
}

package vn.fs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.fs.entity.Notification;
import vn.fs.repository.NotificationRepository;
import vn.fs.service.NotificationSocketTickets;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
}, showSql = false)
@Import(NotificationApi.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationApiPersistenceTest {
    @Autowired NotificationApi api;
    @Autowired NotificationRepository notifications;
    @MockBean NotificationSocketTickets socketTickets;
    MockMvc mvc;

    @BeforeEach void setup() {
        notifications.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    private Notification notice(boolean seen) {
        return notifications.saveAndFlush(new Notification(null, "Đơn thử nghiệm", new Date(), seen));
    }

    @Test void marksAndPersistsOneReadWithoutAnOpenRequestSession() throws Exception {
        var saved = notice(false);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        mvc.perform(get("/api/notification/readed/" + saved.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        assertThat(notifications.findById(saved.getId()).orElseThrow().getStatus()).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test void returnsNotFoundForAMissingNotification() throws Exception {
        mvc.perform(get("/api/notification/readed/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test void marksAllReadInAWriteTransaction() throws Exception {
        notice(false);
        notice(false);
        notice(true);
        mvc.perform(get("/api/notification/read-all")).andExpect(status().isOk());
        assertThat(notifications.findAll()).hasSize(3).allMatch(n -> Boolean.TRUE.equals(n.getStatus()));
    }
}

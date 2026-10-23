package vn.fs.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.fs.automation.*;
import vn.fs.config.*;
import vn.fs.service.CatalogDemandService;
import vn.fs.service.implement.UserDetailsServiceImpl;

/** Actual route and method-security chain; all principals and services are isolated test fixtures. */
@WebMvcTest(controllers={AutomationApi.class,AutomationAdminApi.class,CatalogDemandApi.class})
@Import({WebSecurityConfig.class,AuthEntryPointJwt.class})
class AutomationApiAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AutomationService service;
    @MockBean AutomationRunner runner;
    @MockBean CatalogDemandService demand;
    @MockBean JwtUtils jwt;
    @MockBean UserDetailsServiceImpl userDetails;

    @BeforeEach void clearContextStartupInteractions() {
        // Boot's ApplicationReady listener is outside each HTTP request under test.
        clearInvocations(service,demand,runner);
    }

    @Test void anonymousCannotReadAnyAutomationDataOrWriteInbox() throws Exception {
        for(String url:List.of("/api/automations/me/inbox","/api/automations/me/preferences","/api/admin/automations","/api/admin/catalog-demand"))
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/automations/me/inbox/seen")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,demand,runner);
    }

    @Test @WithMockUser(roles="USER")
    void regularReaderCannotReadOrChangeAdminRules() throws Exception {
        mvc.perform(get("/api/admin/automations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/catalog-demand")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/automations/run")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/automations/rules/LOW_STOCK").contentType("application/json").content("{\"enabled\":true,\"threshold\":5}")).andExpect(status().isForbidden());
        verifyNoInteractions(service,demand,runner);
    }

    @Test @WithMockUser(roles="USER")
    void inboxContractBindsServerPaginationAndFilters() throws Exception {
        when(service.inbox(2,20,"PRICE_DROP",true)).thenReturn(Map.of("items",List.of(),"total",0,"unreadCount",3));
        mvc.perform(get("/api/automations/me/inbox").param("page","2").param("type","PRICE_DROP").param("unread","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.unreadCount").value(3));
        verify(service).inbox(2,20,"PRICE_DROP",true);
    }

    @Test @WithMockUser(roles="USER")
    void readAllUsesDedicatedRouteAndEmailOptOutRemainsExplicit() throws Exception {
        mvc.perform(put("/api/automations/me/inbox/seen")).andExpect(status().isOk());
        verify(service).seenAll();verify(service,never()).seen(anyString());
        when(service.savePreferences(any())).thenReturn(Map.of("emailEnabled",false));
        mvc.perform(put("/api/automations/me/preferences").contentType("application/json").content("{\"emailEnabled\":false,\"followedAuthors\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.emailEnabled").value(false));
        verify(service).savePreferences(argThat(input -> Boolean.FALSE.equals(input.getEmailEnabled())));
    }

    @Test @WithMockUser(roles="ADMIN")
    void adminCanReadReportsAndRunManualScan() throws Exception {
        when(service.admin()).thenReturn(Map.of("rules",List.of(),"recentRuns",List.of()));
        when(demand.summary()).thenReturn(Map.of("days",30,"items",List.of()));
        mvc.perform(get("/api/admin/automations")).andExpect(status().isOk()).andExpect(jsonPath("$.rules").isArray());
        mvc.perform(get("/api/admin/catalog-demand")).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(30));
        mvc.perform(post("/api/admin/automations/run")).andExpect(status().isOk());
        verify(runner).manual();
    }

    @Test void invalidBearerDoesNotGrantEitherRole() throws Exception {
        when(jwt.validateJwtToken("synthetic-invalid-test-token")).thenReturn(false);
        mvc.perform(get("/api/admin/automations").header("Authorization","Bearer synthetic-invalid-test-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,demand,runner);
    }
}

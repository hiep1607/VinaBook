package vn.fs.api;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.fs.service.OrderCancellationService;

class MyOrderActionsApiTest {
    final OrderCancellationService service=mock(OrderCancellationService.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new MyOrderActionsApi(service)).build();
    @Test void rejectsAbsentOrFalseConfirmation() throws Exception {
        for(String body:new String[]{"{}","{\"confirmed\":false}"})
            mvc.perform(post("/api/orders/me/7/cancel").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void confirmationCallsSharedDomainService() throws Exception {
        when(service.cancel(7L)).thenReturn(Map.of("orderId",7L,"status","CANCELLED"));
        mvc.perform(post("/api/orders/me/7/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(service,times(1)).cancel(7L);
    }
    @Test void previewNeverCallsCancellation() throws Exception {
        when(service.previewDetails(7L)).thenReturn(Map.of("orderId",7L,"canCancel",true));
        mvc.perform(get("/api/orders/me/7/cancellation")).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(7));
        verify(service,never()).cancel(any());
    }
    @Test void browserRequestAllowsOnlyTheLocalConfirmationOriginWithoutExecutingIt() throws Exception {
        mvc.perform(post("/api/orders/me/7/cancel").header("Origin","http://localhost:4200")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));
        mvc.perform(post("/api/orders/me/7/cancel").header("Origin","https://unrelated.invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}

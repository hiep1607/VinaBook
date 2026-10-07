package vn.fs.api;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.fs.service.CustomerSupportService;
class SupportRequestsApiTest {
    CustomerSupportService service=mock(CustomerSupportService.class);
    org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new SupportRequestsApi(service)).build();
    @Test void refusesUnconfirmedAndMalformedRequestsBeforeBusinessLogic() throws Exception {
        mvc.perform(post("/api/support/me").contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"test-key-123\",\"kind\":\"SUPPORT\",\"message\":\"Nhờ kiểm tra giúp\",\"confirmed\":false}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/support/me").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void requestsSupportTheLocalStorefrontAndAdminAndRefuseOtherOrigins() throws Exception {
        mvc.perform(post("/api/support/me").header("Origin","http://localhost:4200")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));
        mvc.perform(put("/api/support/admin/7").header("Origin","http://localhost:4300")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/support/me").header("Origin","https://unrelated.invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}

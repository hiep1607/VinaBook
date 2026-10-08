package vn.fs.service;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.fs.api.*;
class CustomerFeatureErrorsTest {
 @Test void returnsFriendlyBusinessReasonWithoutStackTrace() throws Exception {
  RestockWatchService stock=mock(RestockWatchService.class);when(stock.watch(7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Sách đang có hàng. Bạn có thể mở sách để mua ngay nhé."));
  var mvc=MockMvcBuilders.standaloneSetup(new RestockWatchApi(stock)).setControllerAdvice(new CustomerFeatureErrors()).build();
  mvc.perform(put("/api/restock/me/7")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Sách đang có hàng. Bạn có thể mở sách để mua ngay nhé.")).andExpect(jsonPath("$.trace").doesNotExist());
 }
}

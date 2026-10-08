package vn.fs.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
class CustomerPreferencesTest {
    CurrentUserAccess access=mock(CurrentUserAccess.class);CustomerPreferencesRepository preferences=mock(CustomerPreferencesRepository.class);
    FavoriteRepository favorites=mock(FavoriteRepository.class);ProductRepository products=mock(ProductRepository.class);EntityManager em=mock(EntityManager.class);
    CustomerPreferencesService service=new CustomerPreferencesService(access,preferences,favorites,products,em);
    User user=new User();CustomerPreferences saved=new CustomerPreferences();
    @BeforeEach void setup(){user.setUserId(1L);saved.setOwnerId(1L);when(access.currentUserId()).thenReturn(1L);when(em.find(User.class,1L)).thenReturn(user);when(em.find(User.class,1L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);when(preferences.findById(1L)).thenReturn(Optional.of(saved));when(favorites.findByUser(user)).thenReturn(List.of());when(preferences.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));}
    CustomerPreferencesService.Address address(){var a=new CustomerPreferencesService.Address();a.setId(1L);a.setName("Người đọc");a.setLabel("Nhà");a.setPhone("0900000000");a.setStreet("Hà Nội, Việt Nam");a.setDefaultAddress(true);return a;}
    @Test void anonymousCannotReadOrMutate(){when(access.currentUserId()).thenReturn(null);assertThatThrownBy(()->service.get()).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(em,preferences,favorites);}
    @Test void onlyPrincipalOwnerIsQueried(){assertThat(service.get()).containsKeys("addresses","wishlistProductIds","avatar","revision").doesNotContainKeys("user","token","email");verify(preferences).findById(1L);verify(favorites).findByUser(user);}
    @Test void staleRevisionCannotOverwriteAnotherDevice(){saved.setRevision(3);var input=new CustomerPreferencesService.AddressUpdate();input.setRevision(2);input.setAddresses(List.of(address()));assertThatThrownBy(()->service.saveAddresses(input)).isInstanceOf(ResponseStatusException.class);verify(preferences,never()).saveAndFlush(any());}
    @Test void validatesAddressesAndKeepsIsDefaultJsonContract() throws Exception {var a=address();CustomerPreferencesService.validateAddresses(List.of(a));String json=new ObjectMapper().writeValueAsString(a);assertThat(json).contains("\"isDefault\":true").doesNotContain("defaultAddress");var b=address();b.setId(2L);assertThatThrownBy(()->CustomerPreferencesService.validateAddresses(List.of(a,b))).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->CustomerPreferencesService.validateAvatar("data:image/svg+xml;base64,PHN2Zz4=" )).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->CustomerPreferencesService.validateAvatar("data:image/png;base64,"+Base64.getEncoder().encodeToString(new byte[20]))).isInstanceOf(ResponseStatusException.class);}
    @Test void migrationIsIdempotentAndNeverReplacesExistingAvatar(){saved.setAvatar("saved-avatar");var input=new CustomerPreferencesService.Migration();input.setMigrationKey("import-stable-key");input.setAddresses(List.of(address()));service.migrate(input);service.migrate(input);verify(preferences,times(1)).saveAndFlush(any());assertThat(saved.getAvatar()).isEqualTo("saved-avatar");assertThat(service.get().get("addresses")).asList().hasSize(1);}
    @Test void repeatedWishlistAddDoesNotDuplicateExistingRows(){var product=new Product();product.setProductId(7L);product.setStatus(true);when(products.findById(7L)).thenReturn(Optional.of(product));var favorite=new Favorite();favorite.setUser(user);favorite.setProduct(product);when(favorites.findByProductAndUser(product,user)).thenReturn(favorite);service.wishlist(7L,true);service.wishlist(7L,true);verify(favorites,never()).save(any());}
}

package vn.fs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;

class RateApiTest {
    private static class Fixture {
        final RateRepository rates=mock(RateRepository.class);
        final ProductRepository products=mock(ProductRepository.class);
        final OrderDetailRepository details=mock(OrderDetailRepository.class);
        final CurrentUserAccess access=mock(CurrentUserAccess.class);
        final EntityManager em=mock(EntityManager.class);
        final RateApi api=new RateApi();
        final User owner=new User();
        final Product product=new Product();
        Fixture() {
            owner.setUserId(7L); owner.setEmail("reader@example.test"); product.setProductId(9L);
            api.rateRepository=rates; api.productRepository=products; api.orderDetailRepository=details;
            api.access=access; api.em=em;
            when(access.currentUserId()).thenReturn(7L);
            when(em.find(User.class,7L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(owner);
            when(products.findById(9L)).thenReturn(Optional.of(product));
            when(rates.save(any(Rate.class))).thenAnswer(call->call.getArgument(0));
        }
        Rate input() {
            Rate input=new Rate(); User clientUser=new User(); clientUser.setUserId(7L);
            input.setUser(clientUser); input.setProduct(product); input.setRating(4.0); input.setComment("  Dễ đọc.  ");
            return input;
        }
        Rate existing(long userId) {
            Rate stored=new Rate(); stored.setId(11L); User user=new User(); user.setUserId(userId);
            stored.setUser(user); stored.setProduct(product); stored.setRating(3.0); stored.setComment("Bản cũ");
            when(rates.findById(11L)).thenReturn(Optional.of(stored)); return stored;
        }
    }

    @Test void creationUsesCanonicalJwtOwnerAndTrimsTheComment() {
        Fixture f=new Fixture(); Rate input=f.input();
        var response=f.api.post(input);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Rate saved=response.getBody();
        assertThat(saved).isNotSameAs(input);
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUser()).isSameAs(f.owner);
        assertThat(saved.getProduct()).isSameAs(f.product);
        assertThat(saved.getComment()).isEqualTo("Dễ đọc.");
        var sequence=inOrder(f.em,f.rates);
        sequence.verify(f.em).find(User.class,7L,LockModeType.PESSIMISTIC_WRITE);
        sequence.verify(f.rates).findFirstByUser_UserIdAndProduct_ProductIdOrderByIdDesc(7L,9L);
        sequence.verify(f.rates).save(saved);
    }

    @Test void retryingReviewCreationUpdatesTheCustomersExistingReview() {
        Fixture f=new Fixture(); Rate existing=f.existing(7L);
        when(f.rates.findFirstByUser_UserIdAndProduct_ProductIdOrderByIdDesc(7L,9L)).thenReturn(existing);
        Rate retry=f.input(); retry.setRating(5.0);
        var response=f.api.post(retry);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(11L);
        assertThat(response.getBody().getRating()).isEqualTo(5.0);
        verify(f.rates).save(existing); verify(f.rates,never()).save(retry);
    }

    @Test void postCannotOverwriteAnExistingRowBySupplyingAnId() {
        Fixture f=new Fixture(); Rate input=f.input(); input.setId(11L);
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(f.rates,f.em,f.products);
    }

    @Test void postRejectsForgedReviewerAndUnauthenticatedRequests() {
        Fixture f=new Fixture(); Rate input=f.input(); input.getUser().setUserId(8L);
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        when(f.access.currentUserId()).thenReturn(null);
        assertThat(f.api.post(f.input()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(f.rates,f.em,f.products);
    }

    @Test void rejectsInvalidStarsAndOverlongCommentsBeforePersistence() {
        Fixture f=new Fixture(); Rate input=f.input(); input.setRating(4.5);
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        input.setRating(5.0); input.setComment("a".repeat(2001));
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        input.setComment("   ");
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(f.rates,f.em,f.products);
    }

    @Test void acceptsTheAdvertisedTwoThousandCharacterBoundary() {
        Fixture f=new Fixture(); Rate input=f.input(); input.setComment("a".repeat(2000));
        var response=f.api.post(input);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getComment()).hasSize(2000);
    }

    @Test void customerCannotEditAnotherCustomersReview() {
        Fixture f=new Fixture(); f.existing(8L); Rate input=f.input(); input.setId(11L);
        assertThat(f.api.put(input).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(f.rates,never()).save(any());
    }

    @Test void editCannotSilentlyApplyToADifferentBook() {
        Fixture f=new Fixture(); f.existing(7L); Rate input=f.input(); input.setId(11L);
        Product other=new Product(); other.setProductId(10L); input.setProduct(other);
        assertThat(f.api.put(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(f.rates,never()).save(any());
    }

    @Test void editRetainsCanonicalOwnerAndBook() {
        Fixture f=new Fixture(); Rate stored=f.existing(7L); Rate input=f.input(); input.setId(11L);
        var response=f.api.put(input);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(stored);
        assertThat(stored.getUser().getUserId()).isEqualTo(7L);
        assertThat(stored.getProduct()).isSameAs(f.product);
        assertThat(stored.getComment()).isEqualTo("Dễ đọc.");
    }

    @Test void rejectsForeignOrderDetailAndAnOrderDetailForAnotherBook() {
        Fixture f=new Fixture(); Rate input=f.input(); OrderDetail detail=new OrderDetail(); detail.setOrderDetailId(12L);
        Order order=new Order(); User other=new User(); other.setUserId(8L); order.setUser(other);
        detail.setOrder(order); detail.setProduct(f.product); input.setOrderDetail(detail);
        when(f.details.findById(12L)).thenReturn(Optional.of(detail));
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        order.setUser(f.owner); Product different=new Product(); different.setProductId(10L); detail.setProduct(different);
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(f.rates,never()).save(any());
    }

    @Test void malformedReferencesReturnBadRequest() {
        Fixture f=new Fixture(); Rate input=f.input(); input.setOrderDetail(new OrderDetail());
        assertThat(f.api.post(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(f.api.post(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(f.api.put(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(f.details,never()).findById(isNull()); verify(f.rates,never()).save(any());
    }
}

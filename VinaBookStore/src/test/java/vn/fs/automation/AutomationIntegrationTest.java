package vn.fs.automation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.service.MailOutboxStore;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","spring.datasource.url=jdbc:h2:mem:automations;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","automation.email-enabled=false"},showSql=false)
@Import({AutomationService.class,AutomationDataSource.class,vn.fs.api.ProductApi.class,vn.fs.api.FavoritesApi.class})
class AutomationIntegrationTest {
    @Autowired EntityManager em;
    @Autowired AutomationService service;
    @Autowired AutomationDataSource data;
    @Autowired AutomationNoticeRepository notices;
    @Autowired AutomationPreferenceRepository preferences;
    @Autowired AutomationRuleRepository rules;
    @Autowired AutomationStateRepository states;
    @Autowired AutomationRunRepository runs;
    @Autowired vn.fs.api.ProductApi productApi;
    @Autowired vn.fs.api.FavoritesApi favoritesApi;
    @Autowired vn.fs.repository.UserRepository users;
    @Autowired vn.fs.repository.CartRepository carts;
    @Autowired vn.fs.repository.CartDetailRepository cartDetails;
    @Autowired vn.fs.repository.ProductRepository products;
    @MockBean vn.fs.service.BookKnowledgeService knowledge;
    @MockBean CurrentUserAccess access;
    @MockBean MailOutboxStore outbox;
    @MockBean ObjectMapper unusedMapper;
    User reader,other;
    Product book;
    Instant time=Instant.parse("2026-10-21T03:00:00Z");
    ObjectMapper mapper=new ObjectMapper();

    @BeforeEach void setup(){
        // ObjectMapper is a real mapper for persisted checkpoint compatibility across restarts.
        reset(unusedMapper);
        reader=user("reader@example.invalid");other=user("other@example.invalid");book=book("Sách của An","An",100000,10,10);
        when(access.currentUserId()).thenReturn(reader.getUserId());when(access.isAdmin()).thenReturn(false);
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,false,"","","http://localhost:4200");
        service.clock(Clock.fixed(time,ZoneOffset.UTC));service.initialize();
        org.springframework.test.util.ReflectionTestUtils.setField(favoritesApi,"automation",service);
    }
    private User user(String email){var u=new User();u.setEmail(email);u.setName("Bạn đọc");u.setStatus(true);em.persist(u);return u;}
    private Product book(String name,String author,double price,int discount,int stock){var b=new Product();b.setName(name);b.setAuthor(author);b.setPrice(price);b.setDiscount(discount);b.setQuantity(stock);b.setStatus(true);b.setEnteredDate(LocalDate.of(2026,10,21));em.persist(b);return b;}
    private void favorite(User u,Product p){var f=new Favorite();f.setProduct(p);f.setUser(u);em.persist(f);}
    private Order order(int status,Instant placed,Product... books){var o=new Order();o.setUser(reader);o.setStatus(status);o.setOrderDate(Date.from(placed));o.setAmount(90000.0);em.persist(o);for(var p:books){var d=new OrderDetail();d.setOrder(o);d.setProduct(p);d.setQuantity(1);d.setPrice(90000.0);em.persist(d);}return o;}
    private Cart cart(){var c=new Cart();c.setUser(reader);em.persist(c);var d=new CartDetail();d.setCart(c);d.setProduct(book);d.setQuantity(1);d.setPrice(90000.0);em.persist(d);return c;}
    private Cart cartFor(User owner,Product product){var c=new Cart();c.setUser(owner);em.persist(c);var line=new CartDetail();line.setCart(c);line.setProduct(product);line.setQuantity(1);line.setPrice((double)data.book(product).price());em.persist(line);return c;}
    private void adminPrice(Product product,double price,int discount) throws Exception {
        var payload=Map.of("productId",product.getProductId(),"name",product.getName(),"author",product.getAuthor(),"price",price,"discount",discount,"quantity",product.getQuantity(),"enteredDate","2026-10-21");
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(productApi).build().perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/"+product.getProductId()).contentType("application/json").content(mapper.writeValueAsString(payload)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        em.flush();
    }
    private void advanceHours(long hours){time=time.plus(Duration.ofHours(hours));service.clock(Clock.fixed(time,ZoneOffset.UTC));}
    private int scan(){em.flush();return service.scan(false);}
    private List<AutomationNotice> ofType(String type){return notices.findAll().stream().filter(n->n.getType().equals(type)).toList();}
    private void prefs(java.util.function.Consumer<AutomationService.PreferenceInput> update){var input=new AutomationService.PreferenceInput();update.accept(input);service.savePreferences(input);}
    private void preferencesFor(User user,java.util.function.Consumer<AutomationService.PreferenceInput> update){when(access.currentUserId()).thenReturn(user.getUserId());prefs(update);when(access.currentUserId()).thenReturn(reader.getUserId());}
    private void noCategories(AutomationService.PreferenceInput p){p.setRestock(false);p.setPriceDrops(false);p.setCartPriceChanges(false);p.setOrders(false);p.setCartReminders(false);p.setReviewReminders(false);p.setAuthorReleases(false);}
    private AutomationDeliveryGuard guard(){return new AutomationDeliveryGuard(notices,preferences,rules,data,mapper,true);}

    private vn.fs.service.CartSyncService syncedCart(){
        when(access.currentEmail()).thenReturn(reader.getEmail());
        return new vn.fs.service.CartSyncService(access,users,carts,cartDetails,products,new vn.fs.service.ChatRetrievalService(new vn.fs.service.ChatIntentService()),em,service);
    }
    private void replaceCart(vn.fs.service.CartSyncService sync,List<Long> ids){
        var input=new vn.fs.service.CartSyncService.Update();input.setRevision(sync.get().revision());input.setRequestId(UUID.randomUUID().toString());
        input.setItems(ids.stream().map(id->{var line=new vn.fs.service.CartSyncService.Line();line.setProductId(id);line.setQuantity(1);return line;}).toList());sync.replace(input);
    }
    @Test void priceChangedImmediatelyAfterAddingToCartIsNotMissedByFirstScan() throws Exception {
        prefs(p->{noCategories(p);p.setCartPriceChanges(true);});var sync=syncedCart();replaceCart(sync,List.of(book.getProductId()));
        adminPrice(book,80000,10);assertThat(scan()).isEqualTo(1);
        assertThat(ofType("CART_PRICE_CHANGE")).singleElement().satisfies(n->assertThat(n.getMessage()).contains("90.000","72.000"));assertThat(scan()).isZero();
    }
    @Test void favoriteAddedBeforeFirstScanAlsoKeepsItsOriginalPrice() throws Exception {
        var favorite=new Favorite();favorite.setProduct(book);assertThat(favoritesApi.post(favorite).getStatusCode()).isEqualTo(HttpStatus.OK);
        adminPrice(book,80000,10);assertThat(scan()).isEqualTo(1);assertThat(ofType("PRICE_DROP")).singleElement().satisfies(n->assertThat(n.getMessage()).contains("90.000","72.000"));
    }
    @Test void editingQuantityPreservesPendingPriceChangeAndRemoveReaddStartsFresh() throws Exception {
        prefs(p->p.setCartPriceChanges(true));var sync=syncedCart();replaceCart(sync,List.of(book.getProductId()));adminPrice(book,80000,10);
        replaceCart(sync,List.of(book.getProductId()));assertThat(scan()).isEqualTo(1);
        replaceCart(sync,List.of());adminPrice(book,70000,10);replaceCart(sync,List.of(book.getProductId()));assertThat(scan()).isZero();
        adminPrice(book,60000,10);assertThat(scan()).isEqualTo(1);assertThat(ofType("CART_PRICE_CHANGE")).hasSize(2);
    }
    @Test void reenableBeforeScannerDoesNotBackfillChangesWhileOptedOut() throws Exception {
        favorite(reader,book);cart();prefs(p->{p.setPriceDrops(false);p.setCartPriceChanges(false);});scan();adminPrice(book,80000,10);
        prefs(p->{p.setPriceDrops(true);p.setCartPriceChanges(true);});assertThat(scan()).isZero();adminPrice(book,70000,10);assertThat(scan()).isEqualTo(1);
    }

    @Test void firstScanIsQuietAndPreferencesDefaultEmailOff(){favorite(reader,book);order(2,time.minus(Duration.ofDays(10)),book);cart();assertThat(scan()).isZero();advanceHours(200);assertThat(scan()).isEqualTo(1);assertThat(ofType("REVIEW_REMINDER")).isEmpty();assertThat(service.getPreferences()).containsEntry("emailEnabled",false).containsEntry("emailAvailable",false);verifyNoInteractions(outbox);}

    @Test void realDiscountPriceDropIsPersistedAndNotRecreatedAfterRestartOrDeletion(){
        favorite(reader,book);assertThat(scan()).isZero();book.setDiscount(20);assertThat(scan()).isEqualTo(1);
        var notice=ofType("PRICE_DROP").get(0);assertThat(notice.getSourceReference()).isEqualTo("90000:80000");assertThat(notice.getMessage()).contains("90.000","80.000");
        em.flush();em.clear();var restarted=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,false,"","","http://localhost:4200");restarted.clock(Clock.fixed(time,ZoneOffset.UTC));
        assertThat(restarted.scan(false)).isZero();restarted.remove(notice.getId());assertThat(restarted.scan(false)).isZero();assertThat(notices.findById(notice.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test void adminPriceEditNotifiesOnlyOwnersWithMatchingCarts() throws Exception {
        User third=user("third@example.invalid");Product unrelated=book("Sách khác","Khác",50000,0,10);
        preferencesFor(reader,p->p.setCartPriceChanges(true));preferencesFor(third,p->p.setCartPriceChanges(true));preferencesFor(other,p->p.setCartPriceChanges(true));
        cartFor(reader,book);cartFor(other,unrelated);cartFor(third,book);assertThat(scan()).isZero();
        adminPrice(book,80000,10);assertThat(scan()).isEqualTo(2);
        var alerts=ofType("CART_PRICE_CHANGE");assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(AutomationNotice::getOwnerId).containsExactlyInAnyOrder(reader.getUserId(),third.getUserId());
        assertThat(alerts).allSatisfy(n->{assertThat(n.getProductId()).isEqualTo(book.getProductId());assertThat(n.getMessage()).contains("90.000","72.000");});
        when(access.currentUserId()).thenReturn(other.getUserId());assertThat(service.inbox(0,20,"ALL",false).get("total")).isEqualTo(0L);
        assertThatThrownBy(()->service.seen(alerts.get(0).getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(scan()).isZero();
    }

    @Test void sameBookProducesDifferentNotificationsForEachCustomersChosenCategories() throws Exception {
        User third=user("orders-only@example.invalid");
        preferencesFor(reader,p->{noCategories(p);p.setPriceDrops(true);});
        preferencesFor(other,p->{noCategories(p);p.setCartPriceChanges(true);});
        preferencesFor(third,p->{noCategories(p);p.setOrders(true);});
        for(User u:List.of(reader,other,third)){cartFor(u,book);favorite(u,book);}
        var first=order(0,time.minusSeconds(3600),book);var second=order(0,time.minusSeconds(3600),book);second.setUser(other);var last=order(0,time.minusSeconds(3600),book);last.setUser(third);
        assertThat(scan()).isZero();adminPrice(book,80000,10);assertThat(scan()).isEqualTo(2);
        assertThat(notices.findAll()).extracting(n->n.getOwnerId()+":"+n.getType()).containsExactlyInAnyOrder(reader.getUserId()+":PRICE_DROP",other.getUserId()+":CART_PRICE_CHANGE");
        first.setStatus(1);second.setStatus(1);last.setStatus(1);assertThat(scan()).isEqualTo(1);
        assertThat(ofType("ORDER_STATUS")).singleElement().satisfies(n->assertThat(n.getOwnerId()).isEqualTo(third.getUserId()));
        when(access.currentUserId()).thenReturn(third.getUserId());assertThat(service.inbox(0,20,"CART_PRICE_CHANGE",false).get("total")).isEqualTo(0L);
        when(access.currentUserId()).thenReturn(other.getUserId());assertThat(service.inbox(0,20,"CART_PRICE_CHANGE",false).get("total")).isEqualTo(1L);
    }

    @Test void savingOneChoicePreservesOtherDisabledCategoriesAndAuthorSelections(){
        assertThat(service.getPreferences()).containsEntry("cartPriceChanges",false);
        prefs(p->{noCategories(p);p.setFollowedAuthors(List.of("An"));});prefs(p->p.setCartPriceChanges(true));
        assertThat(service.getPreferences()).containsEntry("priceDrops",false).containsEntry("orders",false).containsEntry("cartReminders",false).containsEntry("restock",false).containsEntry("reviewReminders",false).containsEntry("authorReleases",false).containsEntry("cartPriceChanges",true).containsEntry("followedAuthors",List.of("An"));
        when(access.currentUserId()).thenReturn(other.getUserId());assertThat(service.getPreferences()).containsEntry("cartPriceChanges",false).containsEntry("followedAuthors",List.of());
    }

    @Test void cartPriceIncreaseAndDecreaseAreReportedOnceEvenWhenAlsoFavorited() throws Exception {
        prefs(p->p.setCartPriceChanges(true));cart();favorite(reader,book);scan();
        adminPrice(book,120000,10);assertThat(scan()).isEqualTo(1);assertThat(ofType("CART_PRICE_CHANGE").get(0).getMessage()).contains("tăng","90.000","108.000");
        adminPrice(book,80000,10);assertThat(scan()).isEqualTo(1);assertThat(ofType("CART_PRICE_CHANGE")).hasSize(2);assertThat(ofType("PRICE_DROP")).isEmpty();assertThat(scan()).isZero();
        em.flush();em.clear();var restarted=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,false,"","","http://localhost:4200");restarted.clock(Clock.fixed(time,ZoneOffset.UTC));assertThat(restarted.scan(false)).isZero();
    }

    @Test void optingOutDoesNotBackfillOldCartChangesWhenEnabledAgain() throws Exception {
        cart();scan();adminPrice(book,80000,10);assertThat(scan()).isZero();prefs(p->p.setCartPriceChanges(true));assertThat(scan()).isZero();
        adminPrice(book,70000,10);assertThat(scan()).isEqualTo(1);prefs(p->p.setCartPriceChanges(false));adminPrice(book,60000,10);assertThat(scan()).isZero();
        prefs(p->p.setCartPriceChanges(true));assertThat(scan()).isZero();rules.findById("CART_PRICE_CHANGE").orElseThrow().setEnabled(false);adminPrice(book,50000,10);assertThat(scan()).isZero();
        rules.findById("CART_PRICE_CHANGE").orElseThrow().setEnabled(true);assertThat(scan()).isZero();adminPrice(book,40000,10);assertThat(scan()).isEqualTo(1);
    }

    @Test void removingAndReaddingABookDoesNotNotifyAbsentCartsOrLoseFutureEvents() throws Exception {
        prefs(p->p.setCartPriceChanges(true));Cart cart=cart();scan();adminPrice(book,80000,10);assertThat(scan()).isEqualTo(1);
        em.createQuery("delete from CartDetail d where d.cart=:cart").setParameter("cart",cart).executeUpdate();assertThat(scan()).isZero();adminPrice(book,70000,10);assertThat(scan()).isZero();
        cartFor(reader,book);assertThat(scan()).isZero();adminPrice(book,60000,10);assertThat(scan()).isEqualTo(1);assertThat(ofType("CART_PRICE_CHANGE")).hasSize(2);
    }

    @Test void cartEmailsRequireBothCategoryAndEmailOptInForThatOwner() throws Exception {
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,true,"sender@example.invalid","test-only","http://localhost:4200");service.clock(Clock.fixed(time,ZoneOffset.UTC));
        User third=user("email-but-no-category@example.invalid");
        preferencesFor(reader,p->{noCategories(p);p.setCartPriceChanges(true);p.setEmailEnabled(true);p.setEmailAddress("selected@example.invalid");});
        preferencesFor(other,p->{noCategories(p);p.setCartPriceChanges(true);p.setEmailEnabled(false);});
        preferencesFor(third,p->{noCategories(p);p.setEmailEnabled(true);});
        for(User u:List.of(reader,other,third))cartFor(u,book);scan();when(outbox.enqueueJob(any())).thenReturn("cart-price-selected-owner");
        adminPrice(book,80000,10);assertThat(service.scan(true)).isEqualTo(2);
        var mail=org.mockito.ArgumentCaptor.forClass(vn.fs.dto.MailInfo.class);verify(outbox).enqueueJob(mail.capture());assertThat(mail.getValue().getTo()).isEqualTo("selected@example.invalid");
        assertThat(ofType("CART_PRICE_CHANGE")).extracting(AutomationNotice::getOwnerId).containsExactlyInAnyOrder(reader.getUserId(),other.getUserId());
        assertThat(guard().allow("cart-price-selected-owner")).isTrue();prefs(p->p.setCartPriceChanges(false));assertThat(guard().allow("cart-price-selected-owner")).isFalse();
    }

    @Test void disabledPriceRuleDoesNotCreateCatchupSpamWhenEnabledAgain(){favorite(reader,book);scan();rules.findById("PRICE_DROP").orElseThrow().setEnabled(false);book.setDiscount(30);assertThat(scan()).isZero();rules.findById("PRICE_DROP").orElseThrow().setEnabled(true);assertThat(scan()).isZero();book.setDiscount(40);assertThat(scan()).isEqualTo(1);}

    @Test void inactiveAndOutOfStockBooksDoNotGeneratePriceOrRestockAlerts(){favorite(reader,book);scan();book.setQuantity(0);book.setDiscount(20);assertThat(scan()).isZero();book.setStatus(false);book.setQuantity(10);book.setDiscount(30);assertThat(scan()).isZero();}

    @Test void existingUnreadRestockAppearsOnceAndSyncsReadBackToTheWatch(){
        var watch=new RestockWatch();watch.setOwnerId(reader.getUserId());watch.setProductId(book.getProductId());watch.setState("AVAILABLE");watch.setAvailableAt(time.minusSeconds(20));watch.setCreatedAt(time.minusSeconds(60));em.persist(watch);
        assertThat(scan()).isEqualTo(1);assertThat(scan()).isZero();var notice=ofType("RESTOCK").get(0);service.seen(notice.getId());assertThat(watch.isSeen()).isTrue();assertThat(notice.isSeen()).isTrue();
    }

    @Test void disabledRestockEventsAreConsumedAndReadAllSyncsLegacyWatches(){
        var watch=new RestockWatch();watch.setOwnerId(reader.getUserId());watch.setProductId(book.getProductId());watch.setState("AVAILABLE");watch.setAvailableAt(time);watch.setCreatedAt(time.minusSeconds(60));em.persist(watch);
        prefs(p->p.setRestock(false));assertThat(scan()).isZero();prefs(p->p.setRestock(true));assertThat(scan()).isZero();watch.setAvailableAt(time.plusSeconds(10));assertThat(scan()).isEqualTo(1);service.seenAll();em.refresh(watch);assertThat(watch.isSeen()).isTrue();
    }

    @Test void watchedBooksOutsideNewestCatalogSliceStillGenerateAlerts(){
        for(int i=0;i<1000;i++)book("Danh mục "+i,"Tác giả khác",80000,0,5);
        var watch=new RestockWatch();watch.setOwnerId(reader.getUserId());watch.setProductId(book.getProductId());watch.setState("AVAILABLE");watch.setAvailableAt(time);watch.setCreatedAt(time.minusSeconds(60));em.persist(watch);favorite(reader,book);assertThat(scan()).isEqualTo(1);book.setDiscount(20);assertThat(scan()).isEqualTo(1);assertThat(ofType("RESTOCK")).hasSize(1);assertThat(ofType("PRICE_DROP")).hasSize(1);
    }

    @Test void followingAnAuthorBaselinesExistingBooksAndReportsNewCatalogTitles(){
        prefs(p->p.setFollowedAuthors(List.of("An")));assertThat(scan()).isZero();var next=book("Cuốn tiếp theo","An",120000,0,5);assertThat(scan()).isEqualTo(1);
        var notice=ofType("AUTHOR_RELEASE").get(0);assertThat(notice.getProductId()).isEqualTo(next.getProductId());assertThat(notice.getMessage()).contains("thêm vào cửa hàng");assertThat(scan()).isZero();
    }

    @Test void aLongCatalogAuthorNameCannotOverflowSourceReference(){String author="Tác giả "+"An ".repeat(40);book.setAuthor(author.trim());prefs(p->p.setFollowedAuthors(List.of(author.trim())));scan();book("Sách mới của tác giả tên dài",author.trim(),120000,0,5);assertThat(scan()).isEqualTo(1);em.flush();assertThat(ofType("AUTHOR_RELEASE").get(0).getSourceReference()).hasSize(64);}

    @Test void authorPreferencesRejectUnknownAuthorsAndDeduplicateCase(){assertThatThrownBy(()->prefs(p->p.setFollowedAuthors(List.of("Không có")))).isInstanceOf(ResponseStatusException.class);prefs(p->p.setFollowedAuthors(List.of("An"," an ")));assertThat(service.getPreferences().get("followedAuthors")).isEqualTo(List.of("An"));}

    @Test void orderTransitionsAndSevenDayReviewReminderUseRecordedDeliveryObservation(){
        var order=order(0,time.minusSeconds(10),book);scan();order.setStatus(1);assertThat(scan()).isEqualTo(1);order.setStatus(2);assertThat(scan()).isEqualTo(1);
        advanceHours(167);assertThat(scan()).isZero();advanceHours(1);assertThat(scan()).isEqualTo(1);assertThat(ofType("REVIEW_REMINDER")).hasSize(1);assertThat(scan()).isZero();assertThat(ofType("ORDER_STATUS")).hasSize(2);
    }

    @Test void ratedBooksAndOrdersWithReturnRequestsDoNotReceiveReviewReminders(){
        var order=order(1,time.minusSeconds(10),book);scan();order.setStatus(2);scan();var request=new SupportRequest();request.setOwnerId(reader.getUserId());request.setOrderId(order.getOrdersId());request.setKind("RETURN");request.setRequestKey("test-return");request.setMessage("Đổi sách");request.setCreatedAt(time);request.setUpdatedAt(time);em.persist(request);advanceHours(168);scan();assertThat(ofType("REVIEW_REMINDER")).isEmpty();
    }

    @Test void anExistingReviewSuppressesReminderForThatBook(){var order=order(1,time.minusSeconds(10),book);scan();order.setStatus(2);scan();var rate=new Rate();rate.setUser(reader);rate.setProduct(book);rate.setRating(5.0);em.persist(rate);advanceHours(168);scan();assertThat(ofType("REVIEW_REMINDER")).isEmpty();}

    @Test void cartReminderWaitsForInactivityAndDoesNotRepeat(){cart();scan();advanceHours(23);assertThat(scan()).isZero();advanceHours(1);assertThat(scan()).isEqualTo(1);advanceHours(24);assertThat(scan()).isZero();assertThat(ofType("CART_REMINDER")).hasSize(1);}

    @Test void purchaseSuppressesCartReminderUntilCartChangesAgain(){var cart=cart();scan();advanceHours(2);order(0,time,book);scan();advanceHours(30);scan();assertThat(ofType("CART_REMINDER")).isEmpty();advanceHours(1);cart.setSyncRevision(1);scan();advanceHours(24);scan();assertThat(ofType("CART_REMINDER")).hasSize(1);}

    @Test void customerInboxAndWritesAreOwnerScopedAndPaginated(){
        favorite(reader,book);scan();book.setDiscount(20);scan();var notice=ofType("PRICE_DROP").get(0);when(access.currentUserId()).thenReturn(other.getUserId());assertThat(service.inbox(0,20,"ALL",false).get("total")).isEqualTo(0L);assertThatThrownBy(()->service.seen(notice.getId())).isInstanceOf(ResponseStatusException.class).extracting(e->((ResponseStatusException)e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(notice.isSeen()).isFalse();
        when(access.currentUserId()).thenReturn(reader.getUserId());assertThat(service.inbox(0,1,"PRICE_DROP",true).get("total")).isEqualTo(1L);service.seenAll();assertThat(service.summary().get("unreadCount")).isZero();
    }

    @Test void anonymousAndInvalidPaginationRequestsAreRejected(){when(access.currentUserId()).thenReturn(null);assertThatThrownBy(()->service.summary()).isInstanceOf(ResponseStatusException.class).extracting(e->((ResponseStatusException)e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);when(access.currentUserId()).thenReturn(reader.getUserId());assertThatThrownBy(()->service.inbox(-1,20,"ALL",false)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.inbox(0,200,"ALL",false)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.inbox(0,20,"UNKNOWN",false)).isInstanceOf(ResponseStatusException.class);}

    @Test void adminRulesAndLowStockReportAreProtectedAndHaveValidatedBounds(){
        assertThatThrownBy(()->service.admin()).isInstanceOf(ResponseStatusException.class).extracting(e->((ResponseStatusException)e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        when(access.isAdmin()).thenReturn(true);var input=new AutomationService.RuleInput();input.setEnabled(true);input.setThreshold(2);service.saveRule("LOW_STOCK",input);book.setQuantity(1);em.flush();var result=service.admin();assertThat((List<?>)result.get("lowStock")).hasSize(1);assertThat(((Map<?,?>)result.get("stats")).get("unreadLowStock")).isEqualTo(1L);input.setThreshold(-1);assertThatThrownBy(()->service.saveRule("LOW_STOCK",input)).isInstanceOf(ResponseStatusException.class);input.setDelayHours(721);assertThatThrownBy(()->service.saveRule("CART_REMINDER",input)).isInstanceOf(ResponseStatusException.class);assertThat(rules.count()).isEqualTo(8);
    }

    @Test void manualScanNeverQueuesEmailEvenWithCustomerOptInAndConfiguredSmtp(){
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,true,"sender@example.invalid","test-only","http://localhost:4200");service.clock(Clock.fixed(time,ZoneOffset.UTC));prefs(p->p.setEmailEnabled(true));favorite(reader,book);scan();book.setDiscount(20);scan();verifyNoInteractions(outbox);assertThat(ofType("PRICE_DROP").get(0).getEmailState()).isEqualTo("NOT_REQUESTED");
    }

    @Test void queuedCartEmailIsSuppressedIfCustomerPurchasesBeforeDelivery(){
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,true,"sender@example.invalid","test-only","http://localhost:4200");service.clock(Clock.fixed(time,ZoneOffset.UTC));prefs(p->p.setEmailEnabled(true));cart();scan();advanceHours(24);when(outbox.enqueueJob(any())).thenReturn("mail-cart-test");service.scan(true);assertThat(ofType("CART_REMINDER")).hasSize(1);assertThat(guard().allow("mail-cart-test")).isTrue();order(0,time.plusSeconds(1),book);em.flush();assertThat(guard().allow("mail-cart-test")).isFalse();assertThat(ofType("CART_REMINDER").get(0).getEmailState()).isEqualTo("SUPPRESSED");
    }

    @Test void queuedPriceEmailIsSuppressedWhenPriceReboundsOrWishlistIsRemoved(){
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,true,"sender@example.invalid","test-only","http://localhost:4200");service.clock(Clock.fixed(time,ZoneOffset.UTC));prefs(p->p.setEmailEnabled(true));favorite(reader,book);scan();book.setDiscount(20);em.flush();when(outbox.enqueueJob(any())).thenReturn("mail-price-test");service.scan(true);assertThat(guard().allow("mail-price-test")).isTrue();book.setDiscount(10);em.flush();assertThat(guard().allow("mail-price-test")).isFalse();
    }

    @Test void unrelatedTransactionalMailIsNotBlockedByAutomationPreferences(){assertThat(guard().allow("password-reset-unrelated")).isTrue();}

    @Test void recipientDefaultsToAccountAndCustomEmailSurvivesReloadWithSenderOff(){
        assertThat(service.getPreferences()).containsEntry("emailAddress",reader.getEmail());
        prefs(p->{p.setEmailEnabled(true);p.setEmailAddress("  Chosen@example.invalid  ");});em.flush();em.clear();
        assertThat(service.getPreferences()).containsEntry("emailAddress","chosen@example.invalid").containsEntry("emailEnabled",true).containsEntry("emailAvailable",false);
        when(access.currentUserId()).thenReturn(other.getUserId());
        assertThat(service.getPreferences()).containsEntry("emailAddress",other.getEmail()).containsEntry("emailEnabled",false);verifyNoInteractions(outbox);
    }

    @Test void invalidEmailIsRejectedButNeverPreventsRevokingConsent(){
        for(String value:List.of("invalid","a@b","a@example.invalid\r\nBcc:other@example.invalid","One <one@example.invalid>","one@example.invalid,two@example.invalid","a".repeat(255)+"@example.invalid"))
            assertThatThrownBy(()->prefs(p->{p.setEmailEnabled(true);p.setEmailAddress(value);})).isInstanceOf(ResponseStatusException.class);
        prefs(p->{p.setEmailEnabled(true);p.setEmailAddress("chosen@example.invalid");});
        prefs(p->{p.setEmailEnabled(false);p.setEmailAddress("unfinished");});
        assertThat(service.getPreferences()).containsEntry("emailEnabled",false).containsEntry("emailAddress","chosen@example.invalid");
    }

    @Test void omittedRecipientFromOlderClientsPreservesTheCustomAddressAndAccountChoiceResetsIt(){
        prefs(p->p.setEmailAddress("chosen@example.invalid"));prefs(p->p.setOrders(false));
        assertThat(service.getPreferences()).containsEntry("emailAddress","chosen@example.invalid");
        prefs(p->p.setEmailAddress(reader.getEmail()));assertThat(service.getPreferences()).containsEntry("emailAddress",reader.getEmail());
        assertThat(preferences.findById(reader.getUserId()).orElseThrow().getEmailAddress()).isNull();
    }

    @Test void automationQueuesToTheChosenEmailAndCancelsOldDestinationsAfterAnEdit(){
        service=new AutomationService(access,notices,preferences,rules,states,runs,data,outbox,mapper,true,"sender@example.invalid","test-only","http://localhost:4200");service.clock(Clock.fixed(time,ZoneOffset.UTC));
        prefs(p->{p.setEmailEnabled(true);p.setEmailAddress("chosen@example.invalid");});favorite(reader,book);scan();book.setDiscount(20);em.flush();
        when(outbox.enqueueJob(any())).thenReturn("chosen-recipient-job");service.scan(true);
        var mail=org.mockito.ArgumentCaptor.forClass(vn.fs.dto.MailInfo.class);verify(outbox).enqueueJob(mail.capture());
        assertThat(mail.getValue().getTo()).isEqualTo("chosen@example.invalid");assertThat(mail.getValue().getFrom()).isEqualTo("sender@example.invalid");
        assertThat(guard().allow("chosen-recipient-job")).isTrue();prefs(p->{p.setEmailEnabled(true);p.setEmailAddress("new@example.invalid");});em.flush();
        assertThat(guard().allow("chosen-recipient-job")).isFalse();assertThat(ofType("PRICE_DROP").get(0).getEmailState()).isEqualTo("SUPPRESSED");
    }

    @Test void deletingAnAlertRetainsTheUniqueEventTombstone(){var n=new AutomationNotice();n.setId("first");n.setOwnerId(reader.getUserId());n.setEventKey("test-event");n.setType("PRICE_DROP");n.setTitle("Thông báo");n.setMessage("Nội dung");n.setLink("/books");n.setCreatedAt(time);n.setDeleted(true);notices.saveAndFlush(n);assertThat(notices.existsByOwnerIdAndEventKey(reader.getUserId(),"test-event")).isTrue();var duplicate=new AutomationNotice();duplicate.setId("second");duplicate.setOwnerId(reader.getUserId());duplicate.setEventKey("test-event");duplicate.setType("PRICE_DROP");duplicate.setTitle("Thông báo");duplicate.setMessage("Nội dung");duplicate.setLink("/books");duplicate.setCreatedAt(time);assertThatThrownBy(()->notices.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
}

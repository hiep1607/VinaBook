package vn.fs.automation;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static vn.fs.automation.AutomationDataSource.*;

/** Revalidates at the last moment, including purchases between enqueue and SMTP delivery. */
@Service
public class AutomationDeliveryGuard {
    private final AutomationNoticeRepository notices;private final AutomationPreferenceRepository preferences;private final AutomationRuleRepository rules;private final AutomationDataSource data;private final boolean enabled;private final com.fasterxml.jackson.databind.ObjectMapper json;
    public AutomationDeliveryGuard(AutomationNoticeRepository notices,AutomationPreferenceRepository preferences,AutomationRuleRepository rules,AutomationDataSource data,com.fasterxml.jackson.databind.ObjectMapper json,@Value("${automation.email-enabled:false}") boolean enabled){this.notices=notices;this.preferences=preferences;this.rules=rules;this.data=data;this.json=json;this.enabled=enabled;}
    @Transactional public boolean allow(String job){
        // Unrelated transactional/reset emails retain their existing delivery behavior.
        var matches=notices.findByMailJobId(job);if(matches.isEmpty())return true;var notice=matches.get();
        var p=preferences.findById(notice.getOwnerId()).orElse(null);var user=data.owner(notice.getOwnerId(),false);
        boolean allowed=enabled&&p!=null&&p.isEmailEnabled()&&!notice.isDeleted()&&!notice.isSeen()&&user!=null&&Boolean.TRUE.equals(user.getStatus())&&rules.findById(notice.getType()).map(AutomationRule::isEnabled).orElse(false);
        if(allowed){String destination=AutomationService.recipient(p,user.getEmail());allowed=AutomationService.validEmail(destination)&&(notice.getEmailRecipientHash()==null?destination.equals(AutomationService.normalizeEmail(user.getEmail())):notice.getEmailRecipientHash().equals(AutomationService.hash(destination)));}
        if(allowed){var catalog=data.catalog();var snapshot=data.snapshot(notice.getOwnerId(),catalog);var book=snapshot.referencedBooks().stream().filter(b->b.id().equals(notice.getProductId())).findFirst().orElseGet(()->catalog.stream().filter(b->b.id().equals(notice.getProductId())).findFirst().orElse(null));
            switch(notice.getType()){
                case "CART_REMINDER":allowed=p.isCartReminders()&&!snapshot.cart().books().isEmpty()&&AutomationService.hash(snapshot.cart().fingerprint()).equals(notice.getSourceReference())&&snapshot.orders().stream().noneMatch(o->o.status()!=3&&!o.placedAt().isBefore(notice.getCreatedAt()));break;
                case "REVIEW_REMINDER":allowed=p.isReviewReminders()&&snapshot.orders().stream().anyMatch(o->o.id().equals(notice.getOrderId())&&o.status()==2&&!o.returned()&&!o.ratedProducts().contains(notice.getProductId()));break;
                case "RESTOCK":allowed=p.isRestock()&&AutomationService.sellable(book)&&snapshot.watches().stream().anyMatch(w->w.productId().equals(notice.getProductId())&&w.state().equals("AVAILABLE")&&!w.seen()&&w.availableAt()!=null&&w.availableAt().toString().equals(notice.getSourceReference()));break;
                case "PRICE_DROP":allowed=p.isPriceDrops()&&AutomationService.sellable(book)&&snapshot.favorites().stream().anyMatch(f->f.book().id().equals(notice.getProductId()))&&priceStillValid(book,notice.getSourceReference());break;
                case "CART_PRICE_CHANGE":allowed=p.isCartPriceChanges()&&AutomationService.sellable(book)&&snapshot.cart().books().stream().anyMatch(b->b.id().equals(notice.getProductId()))&&samePrice(book,notice.getSourceReference());break;
                case "AUTHOR_RELEASE":allowed=p.isAuthorReleases()&&AutomationService.sellable(book)&&following(p,book.author());break;
                case "ORDER_STATUS":allowed=p.isOrders()&&snapshot.orders().stream().anyMatch(o->o.id().equals(notice.getOrderId())&&String.valueOf(o.status()).equals(notice.getSourceReference()));break;
                default:allowed=false;
            }
        }
        if(!allowed){notice.setEmailState("SUPPRESSED");notices.save(notice);}return allowed;
    }
    private boolean priceStillValid(Book book,String source){try{return book.price()<=Long.parseLong(source.substring(source.indexOf(':')+1));}catch(Exception invalid){return false;}}
    private boolean samePrice(Book book,String source){try{return book.price()==Long.parseLong(source.substring(source.indexOf(':')+1));}catch(Exception invalid){return false;}}
    private boolean following(AutomationPreference p,String author){try{return Arrays.stream(json.readValue(p.getFollowedAuthors(),String[].class)).anyMatch(a->AutomationService.canonical(a).equals(AutomationService.canonical(author)));}catch(Exception invalid){return false;}}
}

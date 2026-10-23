package vn.fs.automation;

import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="automation_preferences") @Getter @Setter
public class AutomationPreference {
    @Id @Column(name="owner_id") private Long ownerId;
    @Column(nullable=false) private boolean restock=true;
    @Column(name="price_drops",nullable=false) private boolean priceDrops=true;
    @Column(name="cart_price_changes",nullable=false,columnDefinition="boolean default false") private boolean cartPriceChanges;
    @Column(nullable=false) private boolean orders=true;
    @Column(name="cart_reminders",nullable=false) private boolean cartReminders=true;
    @Column(name="review_reminders",nullable=false) private boolean reviewReminders=true;
    @Column(name="author_releases",nullable=false) private boolean authorReleases=true;
    @Column(name="email_enabled",nullable=false) private boolean emailEnabled;
    @Column(name="email_address",length=254) private String emailAddress;
    @Column(name="followed_authors",nullable=false,columnDefinition="LONGTEXT") private String followedAuthors="[]";
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Version private long version;
}

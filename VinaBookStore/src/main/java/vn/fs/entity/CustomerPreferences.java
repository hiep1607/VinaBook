package vn.fs.entity;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="customer_preferences") @Getter @Setter
public class CustomerPreferences {
    @Id @Column(name="owner_id") private Long ownerId;
    @Column(name="addresses_json",nullable=false,columnDefinition="LONGTEXT") private String addressesJson="[]";
    @Column(nullable=false,columnDefinition="LONGTEXT") private String avatar="";
    @Column(name="imports_json",nullable=false,columnDefinition="LONGTEXT") private String importsJson="[]";
    @Version private long revision;
}

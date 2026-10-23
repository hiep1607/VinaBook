package vn.fs.automation;

import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="automation_states") @Getter @Setter
public class AutomationState {
    @Id @Column(name="state_key",length=80) private String stateKey;
    @Column(name="value_json",nullable=false,columnDefinition="LONGTEXT") private String valueJson="{}";
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
}

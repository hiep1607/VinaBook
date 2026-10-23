package vn.fs.automation;

import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="automation_rules") @Getter @Setter
public class AutomationRule {
    @Id @Column(name="rule_key",length=24) private String key;
    @Column(nullable=false,length=160) private String label;
    @Column(nullable=false,length=500) private String description;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="delay_hours") private Integer delayHours;
    private Integer threshold;
    @Version private long version;
}

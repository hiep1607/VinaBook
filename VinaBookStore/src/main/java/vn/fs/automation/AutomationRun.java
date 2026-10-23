package vn.fs.automation;

import java.time.Instant;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="automation_runs",indexes=@Index(name="idx_automation_runs_started",columnList="started_at")) @Getter @Setter
public class AutomationRun {
    @Id @Column(length=36) private String id;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(nullable=false,length=16) private String status;
    @Column(name="created_count",nullable=false) private int createdCount;
    @Column(length=300) private String error;
}

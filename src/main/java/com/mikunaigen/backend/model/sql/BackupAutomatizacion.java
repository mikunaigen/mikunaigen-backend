package com.mikunaigen.backend.model.sql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "backup_automatizacion")
public class BackupAutomatizacion {

    public static final String SINGLETON_ID = "singleton";

    @Id
    private String id = SINGLETON_ID;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 20, nullable = false)
    private String frequency = "DAILY";

    @Column(name = "time_hhmm", length = 5, nullable = false)
    private String timeHHmm = "03:00";

    @Column(name = "notify_email_after_finish", nullable = false)
    private boolean notifyEmailAfterFinish;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_fired_slot_key", length = 32)
    private String lastFiredSlotKey;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_attempt_status", length = 20)
    private String lastAttemptStatus = "NONE";

    @Column(name = "last_workflow_status", length = 20)
    private String lastWorkflowStatus;

    @Column(name = "last_workflow_at")
    private Instant lastWorkflowAt;

    @Column(name = "last_workflow_detail", columnDefinition = "TEXT")
    private String lastWorkflowDetail;
}

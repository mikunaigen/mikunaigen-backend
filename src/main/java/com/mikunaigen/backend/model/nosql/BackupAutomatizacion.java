package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "backup_automatizacion")
public class BackupAutomatizacion {
    public static final String SINGLETON_ID = "singleton";

    @Id
    private String id = SINGLETON_ID;

    private boolean enabled;
    private String frequency = "DAILY";
    private String timeHHmm = "03:00";
    private boolean notifyEmailAfterFinish;

    private Instant updatedAt;

    private String lastFiredSlotKey;
    private Instant lastAttemptAt;
    private String lastAttemptStatus = "NONE";
    private String lastWorkflowStatus;
    private Instant lastWorkflowAt;
    private String lastWorkflowDetail;
}

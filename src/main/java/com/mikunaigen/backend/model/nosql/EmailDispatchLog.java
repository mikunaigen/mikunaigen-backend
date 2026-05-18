package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "email_dispatch_logs")
public class EmailDispatchLog {
    @Id
    private String trackingId;
    private String toEmail;
    private String fromEmail;
    private String subject;
    private String notifyUserId;
    private String status;
    private String stage;
    private String githubOwner;
    private String githubRepo;
    private Integer smtpCode;
    private String errorDetail;
    private Instant createdAt;
    private Instant updatedAt;
}

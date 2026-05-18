package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "frontend_error_logs")
public class FrontendErrorLog {
    @Id
    private String id;
    private Instant createdAt = Instant.now();
    private String level;
    private String source;
    private String message;
    private String stack;
    private String routeUrl;
    private String pageUrl;
    private String requestUrl;
    private String requestMethod;
    private Integer httpStatus;
    private String userId;
    private String userEmail;
    private String userRole;
    private String sessionId;
    private String userAgent;
    private String appVersion;
    private String traceId;
    private String clientIp;
}

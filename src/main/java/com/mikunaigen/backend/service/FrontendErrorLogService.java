package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.FrontendErrorReportRequest;
import com.mikunaigen.backend.model.nosql.FrontendErrorLog;
import com.mikunaigen.backend.repository.nosql.FrontendErrorLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class FrontendErrorLogService {

    private final FrontendErrorLogRepository frontendErrorLogRepository;

    public FrontendErrorLogService(FrontendErrorLogRepository frontendErrorLogRepository) {
        this.frontendErrorLogRepository = frontendErrorLogRepository;
    }

    @Async
    public void saveAsync(FrontendErrorReportRequest body, String clientIp) {
        if (body == null) {
            return;
        }
        FrontendErrorLog doc = new FrontendErrorLog();
        doc.setLevel(limit(body.level(), 20, "ERROR"));
        doc.setSource(limit(body.source(), 30, "frontend"));
        doc.setMessage(limit(body.message(), 8000, "Error sin mensaje"));
        doc.setStack(limit(body.stack(), 30000, null));
        doc.setRouteUrl(limit(body.routeUrl(), 1200, null));
        doc.setPageUrl(limit(body.pageUrl(), 1200, null));
        doc.setRequestUrl(limit(body.requestUrl(), 1200, null));
        doc.setRequestMethod(limit(body.requestMethod(), 20, null));
        doc.setHttpStatus(body.httpStatus());
        doc.setUserId(limit(body.userId(), 120, null));
        doc.setUserEmail(limit(body.userEmail(), 320, null));
        doc.setUserRole(limit(body.userRole(), 60, null));
        doc.setSessionId(limit(body.sessionId(), 240, null));
        doc.setUserAgent(limit(body.userAgent(), 700, null));
        doc.setAppVersion(limit(body.appVersion(), 120, null));
        doc.setTraceId(limit(body.traceId(), 120, null));
        doc.setClientIp(limit(clientIp, 120, null));
        frontendErrorLogRepository.save(doc);
    }

    private static String limit(String value, int max, String fallback) {
        String v = value;
        if (v == null || v.isBlank()) {
            return fallback;
        }
        String t = v.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }
}

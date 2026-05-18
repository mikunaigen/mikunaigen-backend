package com.mikunaigen.backend.dto;

public record FrontendErrorReportRequest(
        String level,
        String source,
        String message,
        String stack,
        String routeUrl,
        String pageUrl,
        String requestUrl,
        String requestMethod,
        Integer httpStatus,
        String userId,
        String userEmail,
        String userRole,
        String sessionId,
        String userAgent,
        String appVersion,
        String traceId
) {
}

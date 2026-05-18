package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.FrontendErrorReportRequest;
import com.mikunaigen.backend.service.FrontendErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/client-errors")
public class FrontendErrorLogController {

    private final FrontendErrorLogService frontendErrorLogService;

    public FrontendErrorLogController(FrontendErrorLogService frontendErrorLogService) {
        this.frontendErrorLogService = frontendErrorLogService;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(
            @RequestBody(required = false) FrontendErrorReportRequest body,
            HttpServletRequest request
    ) {
        String ip = extractClientIp(request);
        frontendErrorLogService.saveAsync(body, ip);
        return ResponseEntity.accepted().body(Map.of("ok", true));
    }

    private String extractClientIp(HttpServletRequest request) {
        String h = request.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int comma = h.indexOf(',');
            return (comma > 0 ? h.substring(0, comma) : h).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}

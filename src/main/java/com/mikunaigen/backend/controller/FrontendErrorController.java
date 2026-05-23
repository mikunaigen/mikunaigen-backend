package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.FrontendErrorReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/client-errors")
public class FrontendErrorController {

    private static final Logger log = LoggerFactory.getLogger(FrontendErrorController.class);

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> reportError(@RequestBody FrontendErrorReportRequest request) {
        log.warn("Frontend Error Reported: Level={}, Source={}, Message={}, Route={}, Stack={}",
                request.level(), request.source(), request.message(), request.routeUrl(), request.stack());
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
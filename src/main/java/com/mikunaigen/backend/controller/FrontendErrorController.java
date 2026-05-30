package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.FrontendErrorReportRequest;
import com.mikunaigen.backend.service.RegistroErrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/client-errors")
public class FrontendErrorController {

    private static final Logger log = LoggerFactory.getLogger(FrontendErrorController.class);

    private final RegistroErrorService registroErrorService;

    public FrontendErrorController(RegistroErrorService registroErrorService) {
        this.registroErrorService = registroErrorService;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> reportError(@RequestBody FrontendErrorReportRequest request) {
        log.warn("Frontend Error Reported: Level={}, Source={}, Message={}, Route={}",
                request.level(), request.source(), request.message(), request.routeUrl());
        registroErrorService.registrarErrorFrontend(request);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

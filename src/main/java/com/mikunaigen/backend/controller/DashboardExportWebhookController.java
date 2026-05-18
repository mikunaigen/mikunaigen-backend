package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.dashboard.DashboardExportSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/dashboard-export")
public class DashboardExportWebhookController {

    private final DashboardExportSnapshotService dashboardExportSnapshotService;

    public DashboardExportWebhookController(DashboardExportSnapshotService dashboardExportSnapshotService) {
        this.dashboardExportSnapshotService = dashboardExportSnapshotService;
    }

    @GetMapping("/{jobId}/snapshot")
    public ResponseEntity<?> snapshot(
            @PathVariable String jobId,
            @RequestHeader(value = "X-Dashboard-Export-Token", required = false) String exportToken
    ) {
        if (exportToken == null || exportToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token requerido."));
        }
        try {
            return ResponseEntity.ok(dashboardExportSnapshotService.buildSnapshot(jobId, exportToken.trim()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}

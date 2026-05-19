package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.dashboard.MikunaigenDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class MikunaigenDashboardController {

    private final MikunaigenDashboardService dashboardService;

    public MikunaigenDashboardController(MikunaigenDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> kpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta
    ) {
        LocalDateTime d0 = desde != null ? desde : LocalDateTime.now().minusDays(30);
        LocalDateTime d1 = hasta != null ? hasta : LocalDateTime.now();
        return ResponseEntity.ok(dashboardService.kpis(d0, d1));
    }
}

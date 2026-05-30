package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.repository.sql.UserRepository;
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
    private final UserRepository userRepository;

    public MikunaigenDashboardController(
            MikunaigenDashboardService dashboardService,
            UserRepository userRepository
    ) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> kpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta
    ) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime d1 = hasta != null ? hasta : ahora;
        LocalDateTime d0 = desde != null ? desde : d1.minusMonths(1);

        LocalDateTime fechaMinima = userRepository.findMinClienteCreatedAt().orElse(null);
        if (fechaMinima != null && d0.isBefore(fechaMinima)) {
            d0 = fechaMinima;
        }
        if (d1.isAfter(ahora)) {
            d1 = ahora;
        }
        if (d0.isAfter(d1)) {
            d0 = d1.minusDays(1);
            if (fechaMinima != null && d0.isBefore(fechaMinima)) {
                d0 = fechaMinima;
            }
        }

        return ResponseEntity.ok(dashboardService.kpis(d0, d1));
    }

    @GetMapping("/prediccion-inventario")
    public ResponseEntity<Map<String, Object>> prediccionInventario() {
        return ResponseEntity.ok(dashboardService.prediccionInventario());
    }
}

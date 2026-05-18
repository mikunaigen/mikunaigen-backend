package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.DashboardExportRequest;
import com.mikunaigen.backend.service.DashboardReportDispatchService;
import com.mikunaigen.backend.service.DashboardExportJobService;
import com.mikunaigen.backend.service.dashboard.AdminDashboardService;
import com.mikunaigen.backend.service.dashboard.InventoryPredictionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);

    private final AdminDashboardService adminDashboardService;
    private final InventoryPredictionService inventoryPredictionService;
    private final DashboardReportDispatchService dashboardReportDispatchService;
    private final DashboardExportJobService dashboardExportJobService;

    public AdminDashboardController(
            AdminDashboardService adminDashboardService,
            InventoryPredictionService inventoryPredictionService,
            DashboardReportDispatchService dashboardReportDispatchService,
            DashboardExportJobService dashboardExportJobService
    ) {
        this.adminDashboardService = adminDashboardService;
        this.inventoryPredictionService = inventoryPredictionService;
        this.dashboardReportDispatchService = dashboardReportDispatchService;
        this.dashboardExportJobService = dashboardExportJobService;
    }

    private static LocalDateTime fromDef(LocalDateTime from) {
        return from != null ? from : LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private static LocalDateTime toDefExclusive(LocalDateTime to) {
        return to != null ? to : LocalDateTime.now().plusMinutes(1);
    }

    @GetMapping("/rango-fechas")
    public ResponseEntity<Map<String, String>> rangoFechas(@RequestParam String pestana) {
        log.info("[DASHBOARD-REQ] GET rango-fechas pestana={}", pestana);
        return ResponseEntity.ok(adminDashboardService.rangoFechasDisponible(pestana));
    }

    @GetMapping("/ventas-pedidos")
    public ResponseEntity<Map<String, Object>> ventasPedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String momentOfDay,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) String weatherCondition
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET ventas-pedidos from={} to={} status={} momento={} diaSemana={} clima={}",
                f0, t0, status, momentOfDay, dayOfWeek, weatherCondition);
        return ResponseEntity.ok(adminDashboardService.ventasPedidos(f0, t0, status, momentOfDay, dayOfWeek, weatherCondition));
    }

    @GetMapping("/inventario-costos")
    public ResponseEntity<Map<String, Object>> inventarioCostos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String categoriaInsumo,
            @RequestParam(required = false) String tipoMovimiento,
            @RequestParam(required = false, defaultValue = "false") boolean soloStockBajo,
            @RequestParam(required = false, defaultValue = "10") double umbralStockBajo
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET inventario-costos from={} to={} categoriaInsumo={} tipoMov={} soloStockBajo={} umbral={}",
                f0, t0, categoriaInsumo, tipoMovimiento, soloStockBajo, umbralStockBajo);
        return ResponseEntity.ok(adminDashboardService.inventarioCostos(f0, t0, categoriaInsumo, tipoMovimiento, soloStockBajo, umbralStockBajo));
    }

    @GetMapping("/productos")
    public ResponseEntity<Map<String, Object>> productos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String categoriaProducto,
            @RequestParam(required = false) Integer estrellasMin,
            @RequestParam(required = false) Double precioMin,
            @RequestParam(required = false) Double precioMax
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET productos from={} to={} categoria={} estrellasMin={} precioMin={} precioMax={}",
                f0, t0, categoriaProducto, estrellasMin, precioMin, precioMax);
        return ResponseEntity.ok(adminDashboardService.productos(f0, t0, categoriaProducto, estrellasMin, precioMin, precioMax));
    }

    @GetMapping("/clientes")
    public ResponseEntity<Map<String, Object>> clientes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regTo,
            @RequestParam(required = false) Integer estrellasFiltro,
            @RequestParam(required = false) Boolean soloRecurrentes
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        LocalDateTime regToEx = regTo != null ? regTo : t0;
        log.info("[DASHBOARD-REQ] GET clientes from={} to={} regFrom={} regTo={} estrellasFiltro={} soloRecurrentes={}",
                f0, t0, regFrom, regToEx, estrellasFiltro, soloRecurrentes);
        return ResponseEntity.ok(adminDashboardService.clientes(f0, t0, regFrom, regToEx, estrellasFiltro, soloRecurrentes));
    }

    @GetMapping("/operacion")
    public ResponseEntity<Map<String, Object>> operacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) UUID cajeroId,
            @RequestParam(required = false) UUID repartidorId
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET operacion from={} to={} cajeroId={} repartidorId={}", f0, t0, cajeroId, repartidorId);
        return ResponseEntity.ok(adminDashboardService.operacion(f0, t0, cajeroId, repartidorId));
    }

    @GetMapping("/seguridad")
    public ResponseEntity<Map<String, Object>> seguridad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rol
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET seguridad from={} to={} status={} rol={}", f0, t0, status, rol);
        return ResponseEntity.ok(adminDashboardService.seguridad(f0, t0, status, rol));
    }

    @GetMapping("/interacciones")
    public ResponseEntity<Map<String, Object>> interacciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String condicionClima,
            @RequestParam(required = false) String segmento,
            @RequestParam(required = false) String userId
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        log.info("[DASHBOARD-REQ] GET interacciones from={} to={} action={} clima={} segmento={} userId={}",
                f0, t0, action, condicionClima, segmento, userId);
        return ResponseEntity.ok(adminDashboardService.interacciones(f0, t0, action, condicionClima, segmento, userId));
    }

    @PostMapping("/export/solicitar")
    public ResponseEntity<?> solicitarExportacion(@RequestBody DashboardExportRequest request) {
        try {
            return ResponseEntity.ok(dashboardReportDispatchService.solicitarGeneracion(request));
        } catch (IllegalArgumentException e) {
            log.warn("[DASHBOARD-REQ] POST export/solicitar rechazado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/export/jobs/{jobId}")
    public ResponseEntity<?> estadoExportacion(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(dashboardExportJobService.estadoPublico(jobId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/inventario-prediccion")
    public ResponseEntity<?> inventarioPrediccion() {
        log.info("[DASHBOARD-REQ] POST inventario-prediccion");
        try {
            return ResponseEntity.ok(inventoryPredictionService.ejecutarPrediccionInventario());
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("[DASHBOARD-REQ] POST inventario-prediccion rechazado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}

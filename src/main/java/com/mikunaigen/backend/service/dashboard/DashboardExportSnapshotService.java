package com.mikunaigen.backend.service.dashboard;

import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.nosql.DashboardExportJob;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.service.DashboardExportJobService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardExportSnapshotService {

    private static final DateTimeFormatter GEN_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("es-PE"));
    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    private final DashboardExportJobService dashboardExportJobService;
    private final AdminDashboardService adminDashboardService;
    private final InventoryPredictionService inventoryPredictionService;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;

    public DashboardExportSnapshotService(
            DashboardExportJobService dashboardExportJobService,
            AdminDashboardService adminDashboardService,
            InventoryPredictionService inventoryPredictionService,
            ConfiguracionSistemaRepository configuracionSistemaRepository
    ) {
        this.dashboardExportJobService = dashboardExportJobService;
        this.adminDashboardService = adminDashboardService;
        this.inventoryPredictionService = inventoryPredictionService;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
    }

    public Map<String, Object> buildSnapshot(String jobId, String exportToken) {
        DashboardExportJob job = dashboardExportJobService.obtenerPorToken(jobId, exportToken);
        Map<String, Object> filters = dashboardExportJobService.parseFilters(job);
        Map<String, Object> tabData = fetchTabData(job.getTab(), filters);
        List<Map<String, Object>> sections = DashboardExportSectionBuilder.buildSections(
                tabData,
                job.isIncludeKpis(),
                job.isIncludeCharts(),
                job.isIncludeTables()
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("header", buildHeader(job, filters));
        out.put("tab", job.getTab());
        out.put("tabLabel", job.getTabLabel());
        out.put("format", job.getFormat());
        out.put("options", Map.of(
                "includeKpis", job.isIncludeKpis(),
                "includeCharts", job.isIncludeCharts(),
                "includeTables", job.isIncludeTables()
        ));
        out.put("sections", sections);
        return out;
    }

    private Map<String, Object> buildHeader(DashboardExportJob job, Map<String, Object> filters) {
        ConfiguracionSistema config = configuracionSistemaRepository.findById("GLOBAL_CONFIG").orElse(null);
        String nombre = config != null && config.getNombreNegocio() != null ? config.getNombreNegocio() : "Restaurante";
        String logo = config != null ? config.getLogoBase64() : null;
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("nombreNegocio", nombre);
        if (logo != null && !logo.isBlank()) {
            h.put("logoBase64", logo);
        }
        h.put("generadoEn", GEN_FMT.format(now));
        h.put("generadoPor", job.getGeneratedBy() != null ? job.getGeneratedBy() : "Administrador");
        h.put("pestana", job.getTabLabel());
        h.put("filtros", buildFilterLabels(filters, job.getTab()));
        return h;
    }

    private List<String> buildFilterLabels(Map<String, Object> filters, String tab) {
        List<String> lines = new ArrayList<>();
        if (!"inventario_prediccion".equals(tab)) {
            addLine(lines, "Desde", filters.get("fromDate"));
            addLine(lines, "Hasta", filters.get("toDate"));
        }
        switch (tab) {
            case "ventas" -> {
                addLine(lines, "Estado pedido", filters.get("status"));
                addLine(lines, "Momento del día", filters.get("momentOfDay"));
                addLine(lines, "Día de semana", filters.get("dayOfWeek"));
                addLine(lines, "Clima", filters.get("weatherCondition"));
            }
            case "inventario" -> {
                addLine(lines, "Categoría insumo", filters.get("categoriaInsumo"));
                addLine(lines, "Tipo movimiento", filters.get("tipoMovimiento"));
                addLine(lines, "Solo stock bajo", boolLabel(filters.get("soloStockBajo")));
                addLine(lines, "Umbral stock", filters.get("umbralStockBajo"));
            }
            case "productos" -> {
                addLine(lines, "Categoría producto", filters.get("categoriaProducto"));
                addLine(lines, "Estrellas mín.", filters.get("estrellasMin"));
                addLine(lines, "Rango precio", filters.get("rangoPrecio"));
            }
            case "clientes" -> {
                addLine(lines, "Registro desde", filters.get("regFrom"));
                addLine(lines, "Registro hasta", filters.get("regTo"));
                addLine(lines, "Solo recurrentes", boolLabel(filters.get("soloRecurrentes")));
            }
            case "seguridad" -> {
                addLine(lines, "Estado login", filters.get("loginStatus"));
                addLine(lines, "Rol", filters.get("rol"));
            }
            case "interacciones" -> {
                addLine(lines, "Acción", filters.get("action"));
                addLine(lines, "Clima", filters.get("condicionClima"));
                addLine(lines, "Segmento", filters.get("segmento"));
            }
            case "inventario_prediccion" -> addLine(lines, "Horizonte UI", filters.get("horizonteInv"));
            default -> {
            }
        }
        if (lines.isEmpty()) {
            lines.add("Sin filtros adicionales");
        }
        return lines;
    }

    private void addLine(List<String> lines, String label, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            if (!"Solo stock bajo".equals(label) && !"Solo recurrentes".equals(label)) {
                return;
            }
        }
        if ("Solo stock bajo".equals(label) || "Solo recurrentes".equals(label)) {
            lines.add(label + ": " + boolLabel(value));
            return;
        }
        lines.add(label + ": " + s);
    }

    private String boolLabel(Object v) {
        if (v instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        return "true".equalsIgnoreCase(String.valueOf(v)) ? "Sí" : "No";
    }

    private Map<String, Object> fetchTabData(String tab, Map<String, Object> filters) {
        return switch (tab) {
            case "ventas" -> adminDashboardService.ventasPedidos(
                    from(filters), toExclusive(filters),
                    str(filters, "status"),
                    str(filters, "momentOfDay"),
                    str(filters, "dayOfWeek"),
                    str(filters, "weatherCondition")
            );
            case "inventario" -> adminDashboardService.inventarioCostos(
                    from(filters), toExclusive(filters),
                    str(filters, "categoriaInsumo"),
                    str(filters, "tipoMovimiento"),
                    bool(filters, "soloStockBajo"),
                    dbl(filters, "umbralStockBajo", 10)
            );
            case "productos" -> adminDashboardService.productos(
                    from(filters), toExclusive(filters),
                    str(filters, "categoriaProducto"),
                    intOrNull(filters, "estrellasMin"),
                    dblOrNull(filters, "precioMin"),
                    dblOrNull(filters, "precioMax")
            );
            case "clientes" -> adminDashboardService.clientes(
                    from(filters), toExclusive(filters),
                    regFrom(filters),
                    regToExclusive(filters),
                    intOrNull(filters, "estrellasFiltro"),
                    boolObj(filters, "soloRecurrentes")
            );
            case "operacion" -> adminDashboardService.operacion(
                    from(filters), toExclusive(filters),
                    null,
                    null
            );
            case "seguridad" -> adminDashboardService.seguridad(
                    from(filters), toExclusive(filters),
                    str(filters, "loginStatus"),
                    str(filters, "rol")
            );
            case "interacciones" -> adminDashboardService.interacciones(
                    from(filters), toExclusive(filters),
                    str(filters, "action"),
                    str(filters, "condicionClima"),
                    str(filters, "segmento"),
                    null
            );
            case "inventario_prediccion" -> inventoryPredictionService.ejecutarPrediccionInventario();
            default -> throw new IllegalArgumentException("Pestaña de reporte no válida.");
        };
    }

    private LocalDateTime from(Map<String, Object> f) {
        String ymd = str(f, "fromDate");
        if (ymd == null || ymd.isBlank()) {
            return LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return LocalDate.parse(ymd).atStartOfDay();
    }

    private LocalDateTime toExclusive(Map<String, Object> f) {
        String ymd = str(f, "toDate");
        if (ymd == null || ymd.isBlank()) {
            return LocalDateTime.now().plusMinutes(1);
        }
        return LocalDate.parse(ymd).plusDays(1).atStartOfDay();
    }

    private LocalDateTime regFrom(Map<String, Object> f) {
        String ymd = str(f, "regFrom");
        if (ymd == null || ymd.isBlank()) {
            return null;
        }
        return LocalDate.parse(ymd).atStartOfDay();
    }

    private LocalDateTime regToExclusive(Map<String, Object> f) {
        String ymd = str(f, "regTo");
        if (ymd == null || ymd.isBlank()) {
            return null;
        }
        return LocalDate.parse(ymd).plusDays(1).atStartOfDay();
    }

    private String str(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private boolean bool(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private Boolean boolObj(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private double dbl(Map<String, Object> f, String key, double def) {
        Object v = f.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Double dblOrNull(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrNull(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

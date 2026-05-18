package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.DashboardExportRequest;
import com.mikunaigen.backend.model.nosql.DashboardExportJob;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardReportDispatchService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private static final Map<String, String> TAB_LABELS = Map.of(
            "ventas", "Ventas y Pedidos",
            "inventario", "Inventario y Costos",
            "inventario_prediccion", "Predicción de Inventario",
            "productos", "Productos",
            "clientes", "Clientes",
            "operacion", "Operación",
            "seguridad", "Seguridad",
            "interacciones", "Interacciones"
    );

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    @Value("${app.backup.b2-key-id}")
    private String keyId;

    @Value("${app.backup.b2-app-key}")
    private String appKey;

    @Value("${app.backup.b2-endpoint}")
    private String endpoint;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    @Value("${app.public.api.base-url:}")
    private String publicApiBaseUrl;

    @Value("${app.backup.notify-secret:}")
    private String notifySecret;

    private final DashboardExportJobService dashboardExportJobService;
    private final UserRepository userRepository;

    public DashboardReportDispatchService(
            DashboardExportJobService dashboardExportJobService,
            UserRepository userRepository
    ) {
        this.dashboardExportJobService = dashboardExportJobService;
        this.userRepository = userRepository;
    }

    public Map<String, Object> solicitarGeneracion(DashboardExportRequest request) {
        String tab = request.getTab() != null ? request.getTab().trim() : "";
        if (!TAB_LABELS.containsKey(tab)) {
            throw new IllegalArgumentException("Pestaña de reporte no válida.");
        }
        String format = normalizeFormat(request.getFormat());
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Credenciales de GitHub no configuradas en el servidor.");
        }
        if (publicApiBaseUrl == null || publicApiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("URL pública del API no configurada.");
        }
        if (notifySecret == null || notifySecret.isBlank()) {
            throw new IllegalArgumentException("Secreto de notificación no configurado.");
        }

        String ext = "PDF".equals(format) ? "pdf" : "xlsx";
        String tabSlug = tab.replace('_', '-');
        String timestamp = TS.format(LocalDateTime.now());
        String fileName = String.format("reporte_dashboard_%s.%s", tabSlug, ext);
        String backupKey = "dashboard_export/" + tab + "/" + fileName.replace("." + ext, "") + "_" + timestamp + "." + ext;
        String exportToken = UUID.randomUUID().toString().replace("-", "");
        String generatedBy = resolveGeneratedBy();

        Map<String, Object> b2 = new HashMap<>();
        b2.put("bucket", bucket);
        b2.put("key_id", keyId);
        b2.put("app_key", appKey);
        b2.put("endpoint", endpoint);

        String notifyUrl = publicApiBaseUrl.replaceAll("/+$", "") + "/api/webhooks/dashboard-report-workflow";
        String snapshotBase = publicApiBaseUrl.replaceAll("/+$", "") + "/api/webhooks/dashboard-export";

        DashboardExportJob job = dashboardExportJobService.crearPendiente(
                tab,
                TAB_LABELS.get(tab),
                format,
                fileName,
                backupKey,
                exportToken,
                generatedBy,
                request.isIncludeKpis(),
                request.isIncludeCharts(),
                request.isIncludeTables(),
                request.getFilters()
        );

        Map<String, Object> notify = Map.of(
                "url", notifyUrl,
                "secret", notifySecret
        );

        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("job_id", job.getId());
        clientPayload.put("tab", tab);
        clientPayload.put("format", format);
        clientPayload.put("backup_key", backupKey);
        clientPayload.put("file_name", fileName);
        clientPayload.put("export_token", exportToken);
        clientPayload.put("snapshot_url", snapshotBase + "/" + job.getId() + "/snapshot");
        clientPayload.put("b2", b2);
        clientPayload.put("notify", notify);

        Map<String, Object> payload = Map.of(
                "event_type", "trigger-dashboard-report",
                "client_payload", clientPayload
        );

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(payload, headers);
        String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
        try {
            restTemplate.postForEntity(url, httpRequest, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo iniciar la generación en GitHub Actions." + e.getMessage());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("accepted", true);
        out.put("jobId", job.getId());
        out.put("tab", tab);
        out.put("tabLabel", TAB_LABELS.get(tab));
        out.put("fileName", fileName);
        out.put("format", format);
        out.put("status", DashboardExportJobService.STATUS_PENDING);
        out.put("message", "Generando reporte... Te avisaremos cuando esté listo.");
        return out;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "PDF";
        }
        String f = format.trim().toUpperCase(Locale.ROOT);
        if ("EXCEL".equals(f) || "XLSX".equals(f) || "CSV".equals(f)) {
            return "EXCEL";
        }
        return "PDF";
    }

    private String resolveGeneratedBy() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            return "ADMIN";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        String name = user != null && user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName().trim()
                : email;
        String role = "ADMIN";
        if (user != null && user.getRole() != null && user.getRole().getName() != null) {
            role = user.getRole().getName().trim().toUpperCase(Locale.ROOT);
        }
        return name + " - " + role;
    }
}

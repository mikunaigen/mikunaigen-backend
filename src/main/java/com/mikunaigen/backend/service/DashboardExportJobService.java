package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.nosql.DashboardExportJob;
import com.mikunaigen.backend.repository.nosql.DashboardExportJobRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class DashboardExportJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private final DashboardExportJobRepository repository;
    private final ObjectMapper objectMapper;

    public DashboardExportJobService(DashboardExportJobRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public DashboardExportJob crearPendiente(
            String tab,
            String tabLabel,
            String format,
            String fileName,
            String backupKey,
            String exportToken,
            String generatedBy,
            boolean includeKpis,
            boolean includeCharts,
            boolean includeTables,
            Map<String, Object> filters
    ) {
        DashboardExportJob job = new DashboardExportJob();
        job.setId(UUID.randomUUID().toString());
        job.setTab(tab);
        job.setTabLabel(tabLabel);
        job.setFormat(format);
        job.setFileName(fileName);
        job.setBackupKey(backupKey);
        job.setExportToken(exportToken);
        job.setGeneratedBy(generatedBy);
        job.setIncludeKpis(includeKpis);
        job.setIncludeCharts(includeCharts);
        job.setIncludeTables(includeTables);
        job.setFiltersJson(serializeFilters(filters));
        job.setStatus(STATUS_PENDING);
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return repository.save(job);
    }

    public DashboardExportJob marcarListo(String jobId, String backupKey, String downloadUrl) {
        DashboardExportJob job = resolverJob(jobId, backupKey);
        job.setStatus(STATUS_READY);
        job.setDownloadUrl(downloadUrl);
        job.setErrorMessage(null);
        job.setUpdatedAt(Instant.now());
        return repository.save(job);
    }

    public DashboardExportJob marcarFallido(String jobId, String backupKey, String errorMessage) {
        DashboardExportJob job = resolverJob(jobId, backupKey);
        job.setStatus(STATUS_FAILED);
        job.setDownloadUrl(null);
        job.setErrorMessage(errorMessage);
        job.setUpdatedAt(Instant.now());
        return repository.save(job);
    }

    public DashboardExportJob obtenerPorToken(String jobId, String exportToken) {
        DashboardExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajo de reporte no encontrado."));
        if (job.getExportToken() == null || !job.getExportToken().equals(exportToken)) {
            throw new IllegalArgumentException("Token de exportación no válido.");
        }
        return job;
    }

    public Map<String, Object> estadoPublico(String jobId) {
        DashboardExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajo de reporte no encontrado."));
        Map<String, Object> out = new HashMap<>();
        out.put("jobId", job.getId());
        out.put("tab", job.getTab());
        out.put("tabLabel", job.getTabLabel());
        out.put("fileName", job.getFileName());
        out.put("format", job.getFormat());
        out.put("status", job.getStatus());
        if (STATUS_READY.equals(job.getStatus())) {
            out.put("downloadUrl", job.getDownloadUrl());
        }
        if (STATUS_FAILED.equals(job.getStatus())) {
            out.put("message", job.getErrorMessage());
        }
        return out;
    }

    public Map<String, Object> parseFilters(DashboardExportJob job) {
        if (job.getFiltersJson() == null || job.getFiltersJson().isBlank()) {
            return new HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(job.getFiltersJson(), Map.class);
            return m != null ? m : new HashMap<>();
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private String serializeFilters(Map<String, Object> filters) {
        try {
            return objectMapper.writeValueAsString(filters != null ? filters : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private DashboardExportJob resolverJob(String jobId, String backupKey) {
        if (jobId != null && !jobId.isBlank()) {
            return repository.findById(jobId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Trabajo de reporte no encontrado."));
        }
        if (backupKey != null && !backupKey.isBlank()) {
            return repository.findByBackupKey(backupKey.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Trabajo de reporte no encontrado."));
        }
        throw new IllegalArgumentException("Identificador de trabajo requerido.");
    }
}

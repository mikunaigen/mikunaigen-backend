package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.model.nosql.DatasetExportJob;
import com.mikunaigen.backend.repository.nosql.DatasetExportJobRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class AiDatasetJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private final DatasetExportJobRepository repository;

    public AiDatasetJobService(DatasetExportJobRepository repository) {
        this.repository = repository;
    }

    public DatasetExportJob crearPendiente(int slot, String fileName, String backupKey) {
        DatasetExportJob job = new DatasetExportJob();
        job.setId(UUID.randomUUID().toString());
        job.setSlot(slot);
        job.setFileName(fileName);
        job.setBackupKey(backupKey);
        job.setStatus(STATUS_PENDING);
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return repository.save(job);
    }

    public DatasetExportJob marcarListo(String jobId, String backupKey, String downloadUrl) {
        DatasetExportJob job = resolverJob(jobId, backupKey);
        job.setStatus(STATUS_READY);
        job.setDownloadUrl(downloadUrl);
        job.setErrorMessage(null);
        job.setUpdatedAt(Instant.now());
        return repository.save(job);
    }

    public DatasetExportJob marcarFallido(String jobId, String backupKey, String errorMessage) {
        DatasetExportJob job = resolverJob(jobId, backupKey);
        job.setStatus(STATUS_FAILED);
        job.setDownloadUrl(null);
        job.setErrorMessage(errorMessage);
        job.setUpdatedAt(Instant.now());
        return repository.save(job);
    }

    public Map<String, Object> estadoPublico(String jobId) {
        DatasetExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajo de dataset no encontrado."));
        Map<String, Object> out = new HashMap<>();
        out.put("jobId", job.getId());
        out.put("slot", job.getSlot());
        out.put("fileName", job.getFileName());
        out.put("status", job.getStatus());
        if (STATUS_READY.equals(job.getStatus())) {
            out.put("downloadUrl", job.getDownloadUrl());
        }
        if (STATUS_FAILED.equals(job.getStatus())) {
            out.put("message", job.getErrorMessage());
        }
        return out;
    }

    private DatasetExportJob resolverJob(String jobId, String backupKey) {
        if (jobId != null && !jobId.isBlank()) {
            return repository.findById(jobId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Trabajo de dataset no encontrado."));
        }
        if (backupKey != null && !backupKey.isBlank()) {
            return repository.findByBackupKey(backupKey.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Trabajo de dataset no encontrado."));
        }
        throw new IllegalArgumentException("Identificador de trabajo requerido.");
    }
}

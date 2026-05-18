package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MaintenanceModeService {

    private final AtomicBoolean maintenance = new AtomicBoolean(false);
    private final SimpMessagingTemplate messagingTemplate;
    private final Set<String> pendingRestoreDbs = ConcurrentHashMap.newKeySet();

    private volatile boolean notifyAdmin;
    private volatile String notifyEmail;
    private volatile Instant startedAt;

    @Value("${app.maintenance.secret:}")
    private String maintenanceSecret;

    public MaintenanceModeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public boolean isMaintenance() {
        return maintenance.get();
    }

    public boolean verifySecret(String header) {
        return maintenanceSecret != null && !maintenanceSecret.isBlank() && maintenanceSecret.equals(header);
    }

    public void startRestore(boolean notifyAdmin, String notifyEmail, Set<String> dbs) {
        this.notifyAdmin = notifyAdmin;
        this.notifyEmail = notifyEmail;
        this.startedAt = Instant.now();
        pendingRestoreDbs.clear();
        if (dbs != null) {
            pendingRestoreDbs.addAll(dbs);
        }
        maintenance.set(true);
        messagingTemplate.convertAndSend("/topic/system", "MAINTENANCE_START");
    }

    public boolean shouldNotifyAdmin() {
        return notifyAdmin && notifyEmail != null && !notifyEmail.isBlank();
    }

    public String notifyEmail() {
        return notifyEmail;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void markRestoreDone(String dbType) {
        if (dbType != null && !dbType.isBlank()) {
            pendingRestoreDbs.remove(dbType.trim().toLowerCase());
        }
    }

    public boolean isRestoreComplete() {
        return pendingRestoreDbs.isEmpty();
    }

    public void endMaintenance() {
        pendingRestoreDbs.clear();
        maintenance.set(false);
        messagingTemplate.convertAndSend("/topic/system", "MAINTENANCE_END");
    }
}


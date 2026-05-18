package com.mikunaigen.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.model.nosql.EmailDispatchLog;
import com.mikunaigen.backend.repository.nosql.EmailDispatchLogRepository;
import com.mikunaigen.backend.util.AesGcmPayloadCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GithubEmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(GithubEmailDispatchService.class);

    private final EmailDispatchLogRepository logRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    @Value("${app.email.dispatch.key-hex:}")
    private String dispatchKeyHex;

    @Value("${app.email.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.public.api.base-url:}")
    private String publicApiBaseUrl;

    public GithubEmailDispatchService(EmailDispatchLogRepository logRepository, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    public void dispatchPlainEmail(
            String to,
            String from,
            String smtpPassword,
            String subject,
            String body,
            String notifyUserId
    ) {
        if (to == null || to.isBlank() || from == null || from.isBlank()
                || smtpPassword == null || smtpPassword.isBlank()) {
            return;
        }
        String trackingId = UUID.randomUUID().toString();
        Map<String, Object> inner = new HashMap<>();
        inner.put("to", to);
        inner.put("from", from);
        inner.put("smtp_user", from);
        inner.put("smtp_password", smtpPassword);
        inner.put("smtp_host", "smtp.gmail.com");
        inner.put("smtp_port", 587);
        inner.put("subject", subject != null ? subject : "");
        inner.put("body", body != null ? body : "");
        inner.put("tracking_id", trackingId);
        inner.put("callback_url", normalizeBaseUrl(publicApiBaseUrl) + "/api/webhooks/email-dispatch");
        inner.put("callback_secret", webhookSecret != null ? webhookSecret : "");

        persistPending(trackingId, to, from, subject, notifyUserId);

        try {
            updateStage(trackingId, "SERIALIZE", "DISPATCHING", null);
            String json = objectMapper.writeValueAsString(inner);
            updateStage(trackingId, "ENCRYPT", "DISPATCHING", null);
            byte[] key = parseHexKey(dispatchKeyHex);
            byte[] enc = AesGcmPayloadCipher.encryptUtf8(json, key);
            String blob = Base64.getEncoder().encodeToString(enc);

            if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
                updateStage(trackingId, "GITHUB_CONFIG", "DISPATCH_ERROR", "GitHub no configurado.");
                throw new EmailDispatchException(trackingId, "GITHUB_CONFIG", "GitHub no configurado.", null);
            }

            Map<String, Object> clientPayload = new HashMap<>();
            clientPayload.put("operation", "send_email");
            clientPayload.put("encrypted_blob", blob);

            Map<String, Object> payload = new HashMap<>();
            payload.put("event_type", "trigger-send-email");
            payload.put("client_payload", clientPayload);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken.trim());
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
            updateStage(trackingId, "GITHUB_DISPATCH", "DISPATCHING", null);
            log.info("Email dispatch {} -> GitHub repo {}/{} event trigger-send-email", trackingId, githubOwner, githubRepo);
            restTemplate.postForEntity(url, request, String.class);
            updateStage(trackingId, "GITHUB_DISPATCH", "DISPATCHED", null);
            log.info("Email dispatch {} encolado en GitHub", trackingId);

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            String msg = "GitHub API " + e.getStatusCode().value() + ": " + (errorBody != null ? errorBody : "");
            updateStage(trackingId, "GITHUB_HTTP", "DISPATCH_ERROR", msg);
            log.error("Email dispatch {} fallo GitHub API {}", trackingId, e.getStatusCode().value());
            throw new EmailDispatchException(trackingId, "GITHUB_HTTP", "Error al encolar correo.", e);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateStage(trackingId, "INTERNAL", "DISPATCH_ERROR", msg);
            log.error("Email dispatch {} fallo interno: {}", trackingId, msg);
            throw new EmailDispatchException(trackingId, "INTERNAL", "Error al encolar correo.", e);
        }
    }

    private void persistPending(String trackingId, String to, String from, String subject, String notifyUserId) {
        EmailDispatchLog log = new EmailDispatchLog();
        log.setTrackingId(trackingId);
        log.setToEmail(to);
        log.setFromEmail(from);
        log.setSubject(subject);
        log.setNotifyUserId(notifyUserId);
        log.setStatus("PENDING");
        log.setStage("CREATED");
        log.setGithubOwner(githubOwner);
        log.setGithubRepo(githubRepo);
        log.setCreatedAt(Instant.now());
        log.setUpdatedAt(Instant.now());
        logRepository.save(log);
    }

    private void updateStage(String trackingId, String stage, String status, String errorDetail) {
        logRepository.findById(trackingId).ifPresent(l -> {
            l.setStage(stage);
            if (status != null) {
                l.setStatus(status);
            }
            if (errorDetail != null && !errorDetail.isBlank()) {
                l.setErrorDetail(errorDetail.length() > 8000 ? errorDetail.substring(0, 8000) : errorDetail);
            }
            l.setUpdatedAt(Instant.now());
            logRepository.save(l);
        });
    }

    private static String normalizeBaseUrl(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:8080";
        }
        String t = base.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static byte[] parseHexKey(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalStateException("EMAIL_DISPATCH_KEY_HEX no configurada.");
        }
        String s = hex.trim();
        if (s.length() != 64) {
            throw new IllegalStateException("EMAIL_DISPATCH_KEY_HEX debe tener 64 caracteres hexadecimales.");
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}

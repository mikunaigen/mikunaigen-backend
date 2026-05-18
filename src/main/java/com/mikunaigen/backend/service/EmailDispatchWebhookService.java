package com.mikunaigen.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.repository.nosql.EmailDispatchLogRepository;
import com.mikunaigen.backend.model.nosql.EmailDispatchLog;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class EmailDispatchWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchWebhookService.class);

    private final EmailDispatchLogRepository logRepository;
    private final ConfiguracionSistemaRepository configRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.email.webhook.secret:}")
    private String webhookSecret;

    public EmailDispatchWebhookService(
            EmailDispatchLogRepository logRepository,
            ConfiguracionSistemaRepository configRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean verifySignature(String header) {
        return webhookSecret != null && !webhookSecret.isBlank() && webhookSecret.equals(header);
    }

    public void handleCallback(Map<String, Object> body) {
        String trackingId = body.get("tracking_id") != null ? String.valueOf(body.get("tracking_id")) : null;
        if (trackingId == null || trackingId.isBlank()) {
            return;
        }
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : "FAILED";
        Integer smtpCode = null;
        if (body.get("smtp_code") instanceof Number n) {
            smtpCode = n.intValue();
        } else if (body.get("smtp_code") != null) {
            try {
                smtpCode = Integer.parseInt(String.valueOf(body.get("smtp_code")));
            } catch (NumberFormatException ignored) {
            }
        }
        String errorDetail = body.get("error_detail") != null ? String.valueOf(body.get("error_detail")) : null;

        Optional<EmailDispatchLog> opt = logRepository.findById(trackingId);
        if (opt.isEmpty()) {
            return;
        }
        EmailDispatchLog log = opt.get();
        log.setStatus(status);
        log.setSmtpCode(smtpCode);
        log.setErrorDetail(errorDetail);
        log.setUpdatedAt(Instant.now());
        logRepository.save(log);
        EmailDispatchWebhookService.log.info("Email callback {} status={} smtpCode={}", trackingId, status, smtpCode);

        if ("SUCCESS".equalsIgnoreCase(status)) {
            configRepository.findById("GLOBAL_CONFIG").ifPresent(cfg -> {
                cfg.setSmtpCredentialsInvalid(false);
                configRepository.save(cfg);
            });
            return;
        }

        if ("FAILED_AUTH".equalsIgnoreCase(status)) {
            configRepository.findById("GLOBAL_CONFIG").ifPresent(cfg -> {
                cfg.setSmtpCredentialsInvalid(true);
                configRepository.save(cfg);
            });
        }

        if ("FAILED_RECIPIENT".equalsIgnoreCase(status) && log.getToEmail() != null) {
            userRepository.findByEmailIgnoreCase(log.getToEmail().trim()).ifPresent(u -> {
                u.setEmailBounced(true);
                userRepository.save(u);
            });
        }

        if (log.getNotifyUserId() != null && !log.getNotifyUserId().isBlank()) {
            try {
                Map<String, Object> msg = new HashMap<>();
                msg.put("userId", log.getNotifyUserId());
                msg.put("kind", "email_dispatch_failed");
                msg.put("message", "No ha sido posible enviar el correo.");
                msg.put("status", status);
                messagingTemplate.convertAndSend("/topic/auth/status", objectMapper.writeValueAsString(msg));
            } catch (Exception ignored) {
            }
        }
    }
}

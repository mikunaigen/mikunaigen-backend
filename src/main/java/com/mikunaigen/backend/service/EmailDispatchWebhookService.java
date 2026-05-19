package com.mikunaigen.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailDispatchWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchWebhookService.class);

    @Value("${app.email.webhook.secret:}")
    private String webhookSecret;

    public boolean verifySignature(String signature) {
        return webhookSecret != null && !webhookSecret.isBlank() && webhookSecret.equals(signature);
    }

    public void handleCallback(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        log.info("Callback email-dispatch: status={}", body.get("status"));
    }
}

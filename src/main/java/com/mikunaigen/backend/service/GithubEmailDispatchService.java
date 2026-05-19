package com.mikunaigen.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.exception.EmailDispatchException;
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

    private final ObjectMapper objectMapper;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    @Value("${app.email.dispatch.key-hex:}")
    private String dispatchKeyHex;

    public GithubEmailDispatchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void enviar(
            String destino,
            String subject,
            String body,
            String emisor,
            String passwordSmtp,
            String notifyUserId
    ) {
        String trackingId = UUID.randomUUID().toString();
        try {
            if (githubToken == null || githubToken.isBlank()) {
                throw new EmailDispatchException(trackingId, "config", "GitHub token no configurado");
            }
            Map<String, Object> emailPayload = new HashMap<>();
            emailPayload.put("to", destino);
            emailPayload.put("subject", subject);
            emailPayload.put("body", body);
            emailPayload.put("from", emisor);
            emailPayload.put("password", passwordSmtp);
            emailPayload.put("trackingId", trackingId);
            emailPayload.put("notifyUserId", notifyUserId);
            emailPayload.put("ts", Instant.now().toString());

            String json = objectMapper.writeValueAsString(emailPayload);
            String encrypted = AesGcmPayloadCipher.encrypt(json, dispatchKeyHex);

            Map<String, Object> clientPayload = Map.of(
                    "payload_b64", Base64.getEncoder().encodeToString(encrypted.getBytes()),
                    "tracking_id", trackingId
            );

            Map<String, Object> payload = Map.of(
                    "event_type", "trigger-email",
                    "client_payload", clientPayload
            );

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
            restTemplate.postForEntity(url, request, String.class);
        } catch (EmailDispatchException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error enviando correo tracking={}", trackingId, e);
            throw new EmailDispatchException(trackingId, "dispatch", e.getMessage());
        }
    }
}

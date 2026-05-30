package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class HuggingFaceInferenciaClient {

    private final RestTemplate restTemplate;

    @Value("${ai.inference.url:}")
    private String inferenceUrl;

    @Value("${ai.inference.token:}")
    private String inferenceToken;

    public HuggingFaceInferenciaClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> formular(Map<String, Object> payload) {
        if (inferenceUrl == null || inferenceUrl.isBlank()) {
            throw new IllegalStateException("URL de inferencia no configurada.");
        }
        String base = inferenceUrl.replaceAll("/$", "");
        String url = base.endsWith("/formulate") ? base : base + "/formulate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (inferenceToken != null && !inferenceToken.isBlank()) {
            headers.setBearerAuth(inferenceToken.trim());
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("La inferencia en Hugging Face no respondió correctamente.");
        }
        return response.getBody();
    }
}

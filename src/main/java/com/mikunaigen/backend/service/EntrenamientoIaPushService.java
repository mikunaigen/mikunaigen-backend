package com.mikunaigen.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EntrenamientoIaPushService {

    public static final String TOPICO = "/topic/admin/entrenamiento-ia";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public EntrenamientoIaPushService(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicar(Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(TOPICO, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ignored) {
        }
    }
}

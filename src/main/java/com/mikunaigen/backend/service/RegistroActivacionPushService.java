package com.mikunaigen.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistroActivacionPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public RegistroActivacionPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notificarActivacion(UUID userId) {
        notificar(userId, "activo", "Cuenta activada correctamente.");
    }

    public void notificarTelefonoNoCoincide(UUID userId) {
        notificar(
                userId,
                "telefono_no_coincide",
                RegistroTelegramService.MSG_TELEFONO_NO_COINCIDE);
    }

    private void notificar(UUID userId, String estado, String message) {
        if (userId == null) {
            return;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("estado", estado);
        payload.put("message", message);
        messagingTemplate.convertAndSend(
                "/topic/registro-activacion/" + userId,
                (Object) payload);
    }
}

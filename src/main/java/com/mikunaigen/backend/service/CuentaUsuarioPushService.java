package com.mikunaigen.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CuentaUsuarioPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public CuentaUsuarioPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notificarCuentaSuspendida(UUID userId) {
        if (userId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tipo", "cuenta_suspendida");
        payload.put(
                "message",
                "Tu cuenta ha sido suspendida por el administrador. No podrás seguir usando la plataforma hasta que sea reactivada."
        );
        messagingTemplate.convertAndSend("/topic/cuenta/usuario/" + userId, (Object) payload);
    }
}

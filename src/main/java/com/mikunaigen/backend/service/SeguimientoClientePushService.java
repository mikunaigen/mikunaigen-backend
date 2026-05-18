package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SeguimientoClientePushService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void enviar(UUID clienteId, String cuerpo) {
        if (clienteId == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/pedidos/" + clienteId, cuerpo);
    }
}

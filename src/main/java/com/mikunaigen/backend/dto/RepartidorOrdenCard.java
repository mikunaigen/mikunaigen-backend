package com.mikunaigen.backend.dto;

public record RepartidorOrdenCard(
        String id,
        String status,
        String createdAt,
        String listoAt,
        String deliveredAt,
        String clienteNombre,
        String direccionEntrega,
        String deliveryPersonId
) {}

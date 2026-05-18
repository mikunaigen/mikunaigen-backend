package com.mikunaigen.backend.dto;

public record CajaOrdenListaItem(
        String id,
        String createdAt,
        String clienteNombre,
        String total
) {}

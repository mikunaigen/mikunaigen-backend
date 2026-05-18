package com.mikunaigen.backend.dto;

import java.util.List;

public record CocinaOrdenCard(
        String id,
        String estado,
        String createdAt,
        String clienteNombre,
        List<CocinaLineaDetalle> lineas
) {}

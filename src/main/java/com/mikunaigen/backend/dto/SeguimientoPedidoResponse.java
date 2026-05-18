package com.mikunaigen.backend.dto;

import java.util.List;

public record SeguimientoPedidoResponse(
        String orderId,
        String estado,
        String createdAt,
        String total,
        String cancelReason,
        String repartidorNombre,
        boolean isRated,
        List<CajaLineaDetalle> lineas
) {}

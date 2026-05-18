package com.mikunaigen.backend.dto;

import java.util.List;

public record SeguimientoPedidoListasResponse(
        List<SeguimientoPedidoResponse> pendientes,
        List<SeguimientoPedidoResponse> finalizados
) {}

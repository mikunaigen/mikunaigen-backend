package com.mikunaigen.backend.dto;

import java.util.List;

public record RepartidorOrdenDetalle(
        String id,
        String status,
        String clienteNombre,
        String clienteTelefono,
        String clienteEmail,
        String direccionEntrega,
        String total,
        List<CajaLineaDetalle> lineas
) {}

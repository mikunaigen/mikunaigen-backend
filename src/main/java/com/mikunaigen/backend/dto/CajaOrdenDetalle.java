package com.mikunaigen.backend.dto;

import java.util.List;

public record CajaOrdenDetalle(
        String id,
        String createdAt,
        String estado,
        String total,
        String clienteNombres,
        String clienteApellidos,
        String clienteEmail,
        String clienteTelefono,
        String direccionEntrega,
        List<CajaLineaDetalle> lineas,
        String comprobanteDataUrl
) {}

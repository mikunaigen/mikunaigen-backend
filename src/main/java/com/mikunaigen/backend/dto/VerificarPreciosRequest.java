package com.mikunaigen.backend.dto;

import java.util.List;

public record VerificarPreciosRequest(
        String userId,
        List<LineaClientePrecio> lineasCliente,
        double totalCliente
) {}

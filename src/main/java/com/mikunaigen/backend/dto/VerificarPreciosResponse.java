package com.mikunaigen.backend.dto;

import java.util.List;

public record VerificarPreciosResponse(
        boolean preciosCambiaron,
        double totalAnterior,
        double totalNuevo,
        List<ItemCambioPrecio> detalleCambios,
        CarritoResponse carritoActualizado
) {}

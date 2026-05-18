package com.mikunaigen.backend.dto;

import java.util.Collections;
import java.util.List;

public record CarritoResponse(List<CarritoLineaResponse> items, List<String> removedItems) {
    public CarritoResponse(List<CarritoLineaResponse> items) {
        this(items, Collections.emptyList());
    }
}

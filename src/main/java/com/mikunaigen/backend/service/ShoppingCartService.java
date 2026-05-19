package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.CarritoResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ShoppingCartService {

    public record LoginCartPayload(CarritoResponse cart, List<String> removedItems) {}

    public LoginCartPayload loadSanitizeAndEnrich(String userId) {
        return new LoginCartPayload(new CarritoResponse(Collections.emptyList(), Collections.emptyList()), List.of());
    }

    public CarritoResponse obtenerCarrito(String userId) {
        return new CarritoResponse(Collections.emptyList(), Collections.emptyList());
    }
}

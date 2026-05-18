package com.mikunaigen.backend.dto;

public record InteraccionUsuarioRequest(
        String userId,
        String productId,
        String action,
        Integer dwellTimeSeconds
) {}

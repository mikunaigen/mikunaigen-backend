package com.mikunaigen.backend.util;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;

import java.util.Set;
import java.util.UUID;

public final class UsuarioCompradorValidator {

    private static final Set<String> ROLES_COMPRA = Set.of(
            "CLIENTE", "ADMIN", "CAJERO", "COCINERO", "REPARTIDOR"
    );

    private UsuarioCompradorValidator() {
    }

    public static User requerirUsuarioComprador(UserRepository userRepository, String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("userId inválido.");
        }
        User user = userRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("Usuario no encontrado."));
        validarUsuarioComprador(user);
        return user;
    }

    public static void validarUsuarioComprador(User user) {
        if (user == null || user.isDeleted()) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        if (user.getRole() == null || user.getRole().getName() == null) {
            throw new IllegalArgumentException("Usuario sin rol asignado.");
        }
        String role = user.getRole().getName().trim().toUpperCase();
        if (!ROLES_COMPRA.contains(role)) {
            throw new IllegalArgumentException("Este usuario no puede realizar pedidos.");
        }
    }
}

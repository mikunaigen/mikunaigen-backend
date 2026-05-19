package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.model.nosql.UserInteraction;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.UserInteractionRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.util.UsuarioCompradorValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class UserInteractionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInteractionRepository userInteractionRepository;

    @Autowired
    private ContextoInteligenciaService contextoInteligenciaService;

    public void registrar(String userId, String productId, String action, Integer dwellTimeSeconds) {
        User user = validarCliente(userId);
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId requerido.");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action requerido.");
        }
        Integer dwell = dwellTimeSeconds;
        if (dwell != null && dwell < 0) {
            dwell = 0;
        }
        ContextoInteligenciaService.ContextoInteligencia ctx = contextoInteligenciaService.contextoActual();
        UserInteraction doc = new UserInteraction();
        doc.setUserId(user.getId().toString());
        doc.setProductId(productId.trim());
        doc.setAction(action.trim().toUpperCase());
        doc.setDwellTimeSeconds(dwell);
        UserInteraction.InteractionContext context = new UserInteraction.InteractionContext();
        context.setTemp(ctx.temp());
        context.setCondition(ctx.condition());
        context.setDay(ctx.day());
        context.setSegment(ctx.segment());
        doc.setContext(context);
        userInteractionRepository.save(doc);
    }

    private User validarCliente(String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (Exception e) {
            throw new IllegalArgumentException("userId inválido.");
        }
        User user = userRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (user.isDeleted() || user.getRole() == null) {
            throw new IllegalArgumentException("No autorizado.");
        }
        UsuarioCompradorValidator.validarUsuarioComprador(user);
        return user;
    }
}

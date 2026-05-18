package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.OrderRating;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.OrderRatingRepository;
import com.mikunaigen.backend.repository.sql.RestaurantOrderRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.util.UsuarioCompradorValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class CalificacionPedidoService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestaurantOrderRepository orderRepository;
    @Autowired
    private OrderRatingRepository orderRatingRepository;

    @Transactional
    public void calificar(UUID userId, UUID orderId, int stars, String comment) {
        User u = requerirCliente(userId);
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Debes calificar con entre 1 y 5 estrellas.");
        }
        RestaurantOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (order.getClient() == null || !order.getClient().getId().equals(u.getId())) {
            throw new IllegalArgumentException("No autorizado a calificar este pedido.");
        }
        if (!"ENTREGADO".equals(order.getStatus())) {
            throw new IllegalArgumentException("Solo puedes calificar pedidos entregados.");
        }
        if (Boolean.TRUE.equals(order.getIsRated()) || orderRatingRepository.existsByOrder_Id(orderId)) {
            throw new IllegalArgumentException("Este pedido ya fue calificado.");
        }
        String c = comment == null ? null : comment.trim();
        if (c != null && c.isEmpty()) {
            c = null;
        }
        OrderRating r = new OrderRating();
        r.setOrder(order);
        r.setStars(stars);
        r.setComment(c);
        orderRatingRepository.save(r);
        order.setIsRated(true);
        orderRepository.save(order);
    }

    private User requerirCliente(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Usuario requerido.");
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted() || u.getRole() == null) throw new IllegalArgumentException("No autorizado.");
        UsuarioCompradorValidator.validarUsuarioComprador(u);
        return u;
    }
}

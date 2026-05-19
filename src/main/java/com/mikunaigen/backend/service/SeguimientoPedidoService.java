package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.CajaLineaDetalle;
import com.mikunaigen.backend.dto.SeguimientoPedidoListasResponse;
import com.mikunaigen.backend.dto.SeguimientoPedidoResponse;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.sql.OrderItem;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.sql.OrderItemRepository;
import com.mikunaigen.backend.repository.sql.RestaurantOrderRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.util.UsuarioCompradorValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class SeguimientoPedidoService {

    private static final List<String> FINALIZADOS = List.of("ENTREGADO", "CANCELADO");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestaurantOrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public SeguimientoPedidoListasResponse listar(UUID userId) {
        User u = requerirCliente(userId);
        List<RestaurantOrder> pend = orderRepository.loadSeguimientoPendientes(u.getId(), FINALIZADOS);
        List<RestaurantOrder> fin = orderRepository.loadSeguimientoFinalizados(u.getId(), FINALIZADOS);
        Map<UUID, List<OrderItem>> byOrder = cargarItemsPorPedido(pend, fin);
        List<SeguimientoPedidoResponse> pRes = pend.stream().map(o -> toResponse(o, byOrder)).toList();
        List<SeguimientoPedidoResponse> fRes = fin.stream().map(o -> toResponse(o, byOrder)).toList();
        return new SeguimientoPedidoListasResponse(pRes, fRes);
    }

    @Transactional(readOnly = true)
    public SeguimientoPedidoResponse obtenerPedidoActual(UUID userId) {
        SeguimientoPedidoListasResponse r = listar(userId);
        if (!r.pendientes().isEmpty()) {
            return r.pendientes().get(0);
        }
        if (!r.finalizados().isEmpty()) {
            return r.finalizados().get(0);
        }
        throw new IllegalArgumentException("No tienes pedidos registrados.");
    }

    private Map<UUID, List<OrderItem>> cargarItemsPorPedido(List<RestaurantOrder> pend, List<RestaurantOrder> fin) {
        List<UUID> allIds = new ArrayList<>();
        for (RestaurantOrder o : pend) {
            allIds.add(o.getId());
        }
        for (RestaurantOrder o : fin) {
            allIds.add(o.getId());
        }
        Map<UUID, List<OrderItem>> byOrder = new HashMap<>();
        if (allIds.isEmpty()) {
            return byOrder;
        }
        for (OrderItem oi : orderItemRepository.findByRestaurantOrder_IdIn(allIds)) {
            UUID oid = oi.getRestaurantOrder().getId();
            byOrder.computeIfAbsent(oid, k -> new ArrayList<>()).add(oi);
        }
        return byOrder;
    }

    private SeguimientoPedidoResponse toResponse(RestaurantOrder order, Map<UUID, List<OrderItem>> byOrder) {
        List<OrderItem> items = byOrder.getOrDefault(order.getId(), List.of());
        List<CajaLineaDetalle> lineas = mapLineas(items);
        String repartidor = "";
        if (order.getDeliveryPerson() != null && order.getDeliveryPerson().getFullName() != null) {
            repartidor = order.getDeliveryPerson().getFullName().trim();
        }
        boolean rated = Boolean.TRUE.equals(order.getIsRated());
        return new SeguimientoPedidoResponse(
                order.getId().toString(),
                order.getStatus(),
                order.getCreatedAt() == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(order.getCreatedAt()),
                order.getTotalPrice() == null ? "0.00" : order.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.getCancelReason() == null ? "" : order.getCancelReason(),
                repartidor,
                rated,
                lineas
        );
    }

    private User requerirCliente(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Usuario requerido.");
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted() || u.getRole() == null) throw new IllegalArgumentException("No autorizado.");
        UsuarioCompradorValidator.validarUsuarioComprador(u);
        return u;
    }

    private List<CajaLineaDetalle> mapLineas(List<OrderItem> items) {
        List<CajaLineaDetalle> out = new ArrayList<>();
        for (OrderItem oi : items) {
            Producto p = productoRepository.findById(oi.getMongoProductId()).orElse(null);
            String nombre = p != null && p.getName() != null ? p.getName() : "Producto";
            int qty = oi.getQuantity() == null ? 0 : oi.getQuantity();
            BigDecimal unit = oi.getPriceAtMoment() == null ? BigDecimal.ZERO : oi.getPriceAtMoment().setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            out.add(new CajaLineaDetalle(nombre, qty, unit.toPlainString(), sub.toPlainString()));
        }
        return out;
    }
}

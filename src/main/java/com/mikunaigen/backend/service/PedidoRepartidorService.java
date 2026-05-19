package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.CajaLineaDetalle;
import com.mikunaigen.backend.dto.RepartidorOrdenCard;
import com.mikunaigen.backend.dto.RepartidorOrdenDetalle;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.sql.OrderItem;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.sql.OrderItemRepository;
import com.mikunaigen.backend.repository.sql.RestaurantOrderRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class PedidoRepartidorService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Autowired
    private RestaurantOrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private SeguimientoClientePushService seguimientoClientePushService;

    @Transactional(readOnly = true)
    public List<RepartidorOrdenCard> listarTablero(UUID userId) {
        User repartidor = requerirRepartidor(userId);
        List<RestaurantOrder> disponibles = orderRepository.findByStatusInOrderByCreatedAtAsc(List.of("PREPARADO"));
        List<RestaurantOrder> propios = orderRepository.findByStatusInAndDeliveryPerson_IdOrderByCreatedAtAsc(
                List.of("PREPARADO", "EN_CAMINO", "ENTREGADO"), repartidor.getId());
        List<RepartidorOrdenCard> out = new ArrayList<>();
        for (RestaurantOrder o : disponibles) {
            if (o.getDeliveryPerson() == null || repartidor.getId().equals(o.getDeliveryPerson().getId())) {
                out.add(mapCard(o));
            }
        }
        for (RestaurantOrder o : propios) {
            if (out.stream().noneMatch(x -> x.id().equals(o.getId().toString()))) {
                out.add(mapCard(o));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public RepartidorOrdenDetalle detalle(UUID orderId, UUID userId) {
        User repartidor = requerirRepartidor(userId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!puedeVer(o, repartidor.getId())) {
            throw new IllegalArgumentException("No autorizado para ver este pedido.");
        }
        String cliente = o.getClient() != null && o.getClient().getFullName() != null ? o.getClient().getFullName() : "";
        String tel = o.getClient() != null && o.getClient().getPhone() != null ? o.getClient().getPhone() : "";
        String email = o.getClient() != null && o.getClient().getEmail() != null ? o.getClient().getEmail() : "";
        String dir = o.getClient() != null && o.getClient().getAddress() != null ? o.getClient().getAddress() : "";
        List<CajaLineaDetalle> lineas = mapLineas(orderItemRepository.findByRestaurantOrder_Id(o.getId()));
        return new RepartidorOrdenDetalle(
                o.getId().toString(),
                o.getStatus(),
                cliente,
                tel,
                email,
                dir,
                o.getTotalPrice() == null ? "0.00" : o.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                lineas
        );
    }

    @Transactional
    public void asumir(UUID orderId, UUID userId) {
        User repartidor = requerirRepartidor(userId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!"PREPARADO".equals(o.getStatus())) {
            throw new IllegalArgumentException("Solo puedes asumir pedidos listos.");
        }
        if (o.getDeliveryPerson() != null && !repartidor.getId().equals(o.getDeliveryPerson().getId())) {
            throw new IllegalArgumentException("Este pedido ya fue asumido por otro repartidor.");
        }
        o.setDeliveryPerson(repartidor);
        o.setDeliveryAssignedAt(LocalDateTime.now());
        orderRepository.save(o);
        messagingTemplate.convertAndSend("/topic/repartidor", "orden_asumida");
        if (o.getClient() != null && o.getClient().getId() != null) {
            seguimientoClientePushService.enviar(o.getClient().getId(), "orden_asumida");
        }
    }

    @Transactional
    public void deshacerAsumido(UUID orderId, UUID userId) {
        User repartidor = requerirRepartidor(userId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!"PREPARADO".equals(o.getStatus())) {
            throw new IllegalArgumentException("El pedido ya cambió de estado.");
        }
        if (o.getDeliveryPerson() == null || !repartidor.getId().equals(o.getDeliveryPerson().getId())) {
            throw new IllegalArgumentException("No puedes deshacer una orden no asumida por ti.");
        }
        o.setDeliveryPerson(null);
        o.setDeliveryAssignedAt(null);
        orderRepository.save(o);
        messagingTemplate.convertAndSend("/topic/repartidor", "asumido_deshacer");
        if (o.getClient() != null && o.getClient().getId() != null) {
            seguimientoClientePushService.enviar(o.getClient().getId(), "asumido_deshacer");
        }
    }

    @Transactional
    public void marcarEnCamino(UUID orderId, UUID userId) {
        User repartidor = requerirRepartidor(userId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!"PREPARADO".equals(o.getStatus())) {
            throw new IllegalArgumentException("Solo puedes mover a En Camino pedidos en preparados.");
        }
        if (o.getDeliveryPerson() == null || !repartidor.getId().equals(o.getDeliveryPerson().getId())) {
            throw new IllegalArgumentException("Este pedido no está asumido por ti.");
        }
        o.setStatus("EN_CAMINO");
        o.setProcessedAt(LocalDateTime.now());
        orderRepository.save(o);
        messagingTemplate.convertAndSend("/topic/repartidor", "orden_en_camino");
        if (o.getClient() != null && o.getClient().getId() != null) {
            seguimientoClientePushService.enviar(o.getClient().getId(), "orden_en_camino");
        }
    }

    @Transactional
    public void marcarEntregado(UUID orderId, UUID userId, byte[] bytes, String contentType) {
        User repartidor = requerirRepartidor(userId);
        validarImagenEntrega(bytes, contentType);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!"EN_CAMINO".equals(o.getStatus())) {
            throw new IllegalArgumentException("Solo pedidos en camino pueden marcarse como entregados.");
        }
        if (o.getDeliveryPerson() == null || !repartidor.getId().equals(o.getDeliveryPerson().getId())) {
            throw new IllegalArgumentException("Este pedido no está asignado a tu usuario.");
        }
        o.setStatus("ENTREGADO");
        o.setDeliveryProofImage(bytes);
        o.setDeliveredAt(LocalDateTime.now());
        o.setProcessedAt(LocalDateTime.now());
        orderRepository.save(o);
        messagingTemplate.convertAndSend("/topic/repartidor", "orden_entregada");
        if (o.getClient() != null && o.getClient().getId() != null) {
            seguimientoClientePushService.enviar(o.getClient().getId(), "orden_entregada");
        }
    }

    private void validarImagenEntrega(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Debes subir una imagen de entrega.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen no debe pesar más de 5MB.");
        }
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (!(ct.contains("jpeg") || ct.contains("jpg") || ct.contains("png"))) {
            throw new IllegalArgumentException("Solo se permiten imágenes en formato JPG o PNG.");
        }
    }

    private boolean puedeVer(RestaurantOrder o, UUID repartidorId) {
        if ("PREPARADO".equals(o.getStatus())) {
            return o.getDeliveryPerson() == null || repartidorId.equals(o.getDeliveryPerson().getId());
        }
        if ("EN_CAMINO".equals(o.getStatus()) || "ENTREGADO".equals(o.getStatus())) {
            return o.getDeliveryPerson() != null && repartidorId.equals(o.getDeliveryPerson().getId());
        }
        return false;
    }

    private RepartidorOrdenCard mapCard(RestaurantOrder o) {
        String cliente = o.getClient() != null && o.getClient().getFullName() != null ? o.getClient().getFullName().trim() : "";
        String dir = o.getClient() != null && o.getClient().getAddress() != null ? o.getClient().getAddress().trim() : "";
        return new RepartidorOrdenCard(
                o.getId().toString(),
                o.getStatus(),
                fmt(o.getCreatedAt()),
                fmt(o.getProcessedAt()),
                fmt(o.getDeliveredAt()),
                cliente,
                dir,
                o.getDeliveryPerson() == null ? "" : o.getDeliveryPerson().getId().toString()
        );
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

    private User requerirRepartidor(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Usuario requerido.");
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted() || u.getRole() == null) throw new IllegalArgumentException("No autorizado.");
        if (!Set.of("REPARTIDOR", "ADMIN").contains(u.getRole().getName())) throw new IllegalArgumentException("No autorizado.");
        return u;
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dt);
    }
}

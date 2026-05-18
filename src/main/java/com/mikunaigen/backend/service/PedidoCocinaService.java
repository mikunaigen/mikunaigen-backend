package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.CocinaIngredienteDetalle;
import com.mikunaigen.backend.dto.CocinaLineaDetalle;
import com.mikunaigen.backend.dto.CocinaOrdenCard;
import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.sql.Inventory;
import com.mikunaigen.backend.model.sql.InventoryMovement;
import com.mikunaigen.backend.model.sql.OrderItem;
import com.mikunaigen.backend.model.sql.Recipe;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.sql.InventoryMovementRepository;
import com.mikunaigen.backend.repository.sql.InventoryRepository;
import com.mikunaigen.backend.repository.sql.OrderItemRepository;
import com.mikunaigen.backend.repository.sql.RecipeRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PedidoCocinaService {

    private static final String EST_COLA = "PAGO_VALIDADO";
    private static final String EST_PREPARACION = "EN_COCINA";
    private static final String EST_LISTO = "PREPARADO";

    @Autowired
    private RestaurantOrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private ConfiguracionSistemaRepository configRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private SeguimientoClientePushService seguimientoClientePushService;

    @Transactional(readOnly = true)
    public List<CocinaOrdenCard> listarTablero(UUID userId) {
        requerirCocinero(userId);
        List<RestaurantOrder> orders = orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(EST_COLA, EST_PREPARACION, EST_LISTO)
        );
        return mapCards(orders);
    }

    @Transactional(readOnly = true)
    public void validarStockParaPreparacion(UUID orderId, UUID userId) {
        requerirCocinero(userId);
        RestaurantOrder order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_COLA.equals(order.getStatus())) {
            throw new IllegalArgumentException("La orden ya no está en cola.");
        }
        List<OrderItem> lines = orderItemRepository.findByRestaurantOrder_Id(orderId);
        validarStockSuficiente(lines);
    }

    @Transactional
    public void confirmarPasoPreparacion(UUID orderId, UUID userId) {
        requerirCocinero(userId);
        RestaurantOrder order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_COLA.equals(order.getStatus())) {
            throw new IllegalArgumentException("La orden ya no puede pasar a preparación.");
        }
        List<OrderItem> lines = orderItemRepository.findByRestaurantOrder_Id(orderId);
        validarStockSuficiente(lines);
        descontarStock(lines, orderId);
        order.setStatus(EST_PREPARACION);
        orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/cocina", "orden_en_cocina");
        if (order.getClient() != null && order.getClient().getId() != null) {
            seguimientoClientePushService.enviar(order.getClient().getId(), "orden_en_cocina");
        }
    }

    @Transactional
    public void marcarListo(UUID orderId, UUID userId) {
        requerirCocinero(userId);
        RestaurantOrder order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_PREPARACION.equals(order.getStatus())) {
            throw new IllegalArgumentException("Solo puedes mover a Listos pedidos en preparación.");
        }
        order.setStatus(EST_LISTO);
        order.setProcessedAt(LocalDateTime.now());
        orderRepository.save(order);
        enviarCorreoRepartidores(order.getId().toString());
        messagingTemplate.convertAndSend("/topic/cocina", "orden_lista");
        messagingTemplate.convertAndSend("/topic/repartidor", "orden_lista");
        if (order.getClient() != null && order.getClient().getId() != null) {
            seguimientoClientePushService.enviar(order.getClient().getId(), "orden_lista");
        }
    }

    private List<CocinaOrdenCard> mapCards(List<RestaurantOrder> orders) {
        List<CocinaOrdenCard> out = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (RestaurantOrder o : orders) {
            List<OrderItem> lines = orderItemRepository.findByRestaurantOrder_Id(o.getId());
            List<CocinaLineaDetalle> lineas = mapLineas(lines);
            String cliente = o.getClient() != null && o.getClient().getFullName() != null
                    ? o.getClient().getFullName().trim() : "";
            out.add(new CocinaOrdenCard(
                    o.getId().toString(),
                    o.getStatus(),
                    o.getCreatedAt() != null ? fmt.format(o.getCreatedAt()) : "",
                    cliente,
                    lineas
            ));
        }
        return out;
    }

    private List<CocinaLineaDetalle> mapLineas(List<OrderItem> lines) {
        List<CocinaLineaDetalle> out = new ArrayList<>();
        for (OrderItem oi : lines) {
            int qty = oi.getQuantity() == null ? 0 : oi.getQuantity();
            Producto p = productoRepository.findById(oi.getMongoProductId()).orElse(null);
            String nombreProd = (p != null && p.getName() != null && !p.getName().isBlank()) ? p.getName() : "Producto";
            List<Recipe> recipes = recipeRepository.findByMongoProductIdAndIsDeletedFalse(oi.getMongoProductId());
            List<CocinaIngredienteDetalle> ingredientes = new ArrayList<>();
            for (Recipe r : recipes) {
                Inventory ing = r.getIngredient();
                if (ing == null || ing.isDeleted()) continue;
                double base = r.getQuantityToSubtract() == null ? 0.0 : r.getQuantityToSubtract();
                ingredientes.add(new CocinaIngredienteDetalle(
                        ing.getName() != null ? ing.getName() : "Ingrediente",
                        base * qty,
                        ing.getUnit() != null ? ing.getUnit() : ""
                ));
            }
            out.add(new CocinaLineaDetalle(oi.getMongoProductId(), nombreProd, qty, ingredientes));
        }
        return out;
    }

    private void validarStockSuficiente(List<OrderItem> lines) {
        Map<Integer, Double> requerido = calcularRequerido(lines);
        for (Map.Entry<Integer, Double> e : requerido.entrySet()) {
            Inventory ing = inventoryRepository.findByIdAndIsDeletedFalse(e.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Falta un insumo de la receta."));
            double stock = ing.getStockQuantity() == null ? 0.0 : ing.getStockQuantity();
            if (stock + 1e-9 < e.getValue()) {
                String nombre = ing.getName() != null ? ing.getName() : "Insumo";
                throw new IllegalArgumentException("Stock insuficiente para " + nombre + ".");
            }
        }
    }

    private void descontarStock(List<OrderItem> lines, UUID orderId) {
        Map<Integer, Double> requerido = calcularRequerido(lines);
        for (Map.Entry<Integer, Double> e : requerido.entrySet()) {
            Inventory ing = inventoryRepository.findByIdAndIsDeletedFalse(e.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Falta un insumo de la receta."));
            double before = ing.getStockQuantity() == null ? 0.0 : ing.getStockQuantity();
            double after = before - e.getValue();
            if (after < -1e-9) {
                throw new IllegalArgumentException("Stock insuficiente para " + (ing.getName() == null ? "insumo" : ing.getName()) + ".");
            }
            ing.setStockQuantity(Math.max(0.0, after));
            inventoryRepository.save(ing);

            InventoryMovement mov = new InventoryMovement();
            mov.setInventoryId(ing.getId());
            mov.setQuantity(BigDecimal.valueOf(-e.getValue()).setScale(2, RoundingMode.HALF_UP));
            mov.setPreviousStock(BigDecimal.valueOf(before).setScale(2, RoundingMode.HALF_UP));
            mov.setNewStock(BigDecimal.valueOf(Math.max(0.0, after)).setScale(2, RoundingMode.HALF_UP));
            mov.setUnitCost(ing.getPrice() == null ? null : BigDecimal.valueOf(ing.getPrice()).setScale(3, RoundingMode.HALF_UP));
            mov.setMovementType("SALIDA");
            mov.setReason("Orden en cocina " + orderId);
            mov.setCreatedAt(LocalDateTime.now());
            movementRepository.save(mov);
        }
    }

    private Map<Integer, Double> calcularRequerido(List<OrderItem> lines) {
        Map<Integer, Double> requerido = new HashMap<>();
        for (OrderItem oi : lines) {
            int qty = oi.getQuantity() == null ? 0 : oi.getQuantity();
            List<Recipe> recipes = recipeRepository.findByMongoProductIdAndIsDeletedFalse(oi.getMongoProductId());
            for (Recipe r : recipes) {
                if (r.getIngredient() == null || r.getIngredient().getId() == null) continue;
                double base = r.getQuantityToSubtract() == null ? 0.0 : r.getQuantityToSubtract();
                requerido.merge(r.getIngredient().getId(), base * qty, Double::sum);
            }
        }
        return requerido;
    }

    private User requerirCocinero(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Usuario requerido.");
        }
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted() || u.getRole() == null) {
            throw new IllegalArgumentException("No autorizado.");
        }
        String role = u.getRole().getName();
        if (!Set.of("COCINERO", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("No autorizado.");
        }
        return u;
    }

    private void enviarCorreoRepartidores(String orderId) {
        List<User> repartidores = userRepository.findByRole_NameAndIsDeletedFalse("REPARTIDOR");
        if (repartidores.isEmpty()) {
            return;
        }
        configRepository.findById("GLOBAL_CONFIG").ifPresent(cfg -> {
            String em = cfg.getEmailSmtp();
            String pw = cfg.getPasswordSmtp();
            if (em == null || em.isBlank() || pw == null || pw.isBlank()) {
                return;
            }
            String negocio = nombreNegocio(cfg);
            String asunto = "Orden lista para entregar — " + negocio;
            String cuerpo = "Se notificó que la orden #" + orderId + " está lista para entregar.";
            for (User r : repartidores) {
                if (r.getEmail() != null && !r.getEmail().isBlank()) {
                    emailService.enviarCorreoTextoPlano(
                            r.getEmail(),
                            asunto,
                            cuerpo,
                            em,
                            pw,
                            r.getId() != null ? r.getId().toString() : null
                    );
                }
            }
        });
    }

    private static String nombreNegocio(ConfiguracionSistema cfg) {
        String n = cfg.getNombreNegocio();
        return (n == null || n.isBlank()) ? "Mikunaigen" : n.trim();
    }
}

package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.CajaLineaDetalle;
import com.mikunaigen.backend.dto.CajaOrdenDetalle;
import com.mikunaigen.backend.dto.CajaOrdenListaItem;
import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.sql.OrderItem;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
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
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class PedidoCajaService {

    private static final String EST_VALIDANDO = "VALIDANDO_PAGO";

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ConfiguracionSistemaRepository configuracionSistemaRepository;

    @Autowired
    private EmailService emailService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private SeguimientoClientePushService seguimientoClientePushService;

    @Transactional(readOnly = true)
    public List<CajaOrdenListaItem> listarPendientesValidacion(UUID processorUserId) {
        requerirProcesadorCaja(processorUserId);
        List<RestaurantOrder> rows = orderRepository.findByStatusOrderByCreatedAtDesc(EST_VALIDANDO);
        List<CajaOrdenListaItem> out = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (RestaurantOrder o : rows) {
            User c = o.getClient();
            String nombre = c.getFullName() != null ? c.getFullName().trim() : "";
            out.add(new CajaOrdenListaItem(
                    o.getId().toString(),
                    o.getCreatedAt() != null ? fmt.format(o.getCreatedAt()) : "",
                    nombre,
                    o.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public CajaOrdenDetalle obtenerDetalle(UUID orderId, UUID processorUserId) {
        requerirProcesadorCaja(processorUserId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_VALIDANDO.equals(o.getStatus())) {
            throw new IllegalArgumentException("Este pedido no está pendiente de validación.");
        }
        User c = o.getClient();
        String[] na = nombresApellidos(c.getFullName());
        List<OrderItem> items = orderItemRepository.findByRestaurantOrder_Id(orderId);
        List<CajaLineaDetalle> lineas = new ArrayList<>();
        for (OrderItem oi : items) {
            String nombreProd = "Producto";
            Producto p = productoRepository.findById(oi.getMongoProductId()).orElse(null);
            if (p != null && p.getName() != null && !p.getName().isBlank()) {
                nombreProd = p.getName();
            }
            BigDecimal unit = oi.getPriceAtMoment() != null
                    ? oi.getPriceAtMoment().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            int qty = oi.getQuantity() != null ? oi.getQuantity() : 0;
            BigDecimal sub = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            lineas.add(new CajaLineaDetalle(
                    nombreProd,
                    qty,
                    unit.toPlainString(),
                    sub.toPlainString()
            ));
        }
        String dataUrl = dataUrlComprobante(o.getPaymentReceiptImage());
        return new CajaOrdenDetalle(
                o.getId().toString(),
                o.getCreatedAt() != null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(o.getCreatedAt()) : "",
                o.getStatus(),
                o.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                na[0],
                na[1],
                c.getEmail() != null ? c.getEmail() : "",
                c.getPhone() != null ? c.getPhone() : "",
                c.getAddress() != null ? c.getAddress() : "",
                lineas,
                dataUrl
        );
    }

    @Transactional
    public void validarPago(UUID orderId, UUID processorUserId) {
        User processor = requerirProcesadorCaja(processorUserId);
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_VALIDANDO.equals(o.getStatus())) {
            throw new IllegalArgumentException("Este pedido no puede validarse en su estado actual.");
        }
        o.setStatus("PAGO_VALIDADO");
        o.setProcessedBy(processor);
        o.setProcessedAt(LocalDateTime.now());
        orderRepository.save(o);
        User client = o.getClient();
        enviarCorreoPagoValidado(client, o.getId().toString(), o.getTotalPrice());
        messagingTemplate.convertAndSend("/topic/caja", "pago_validado");
        messagingTemplate.convertAndSend("/topic/cocina", "pago_validado");
        if (client != null && client.getId() != null) {
            seguimientoClientePushService.enviar(client.getId(), "pago_validado");
        }
    }

    @Transactional
    public void rechazarPago(UUID orderId, UUID processorUserId, String motivo) {
        User processor = requerirProcesadorCaja(processorUserId);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo.");
        }
        RestaurantOrder o = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Pedido no encontrado."));
        if (!EST_VALIDANDO.equals(o.getStatus())) {
            throw new IllegalArgumentException("Este pedido no puede rechazarse en su estado actual.");
        }
        o.setStatus("CANCELADO");
        o.setCancelReason(motivo.trim());
        o.setProcessedBy(processor);
        o.setProcessedAt(LocalDateTime.now());
        orderRepository.save(o);
        User client = o.getClient();
        enviarCorreoPagoRechazado(client, o.getId().toString(), motivo.trim());
        messagingTemplate.convertAndSend("/topic/caja", "pago_rechazado");
        if (client != null && client.getId() != null) {
            seguimientoClientePushService.enviar(client.getId(), "pago_rechazado");
        }
    }

    private User requerirProcesadorCaja(UUID processorUserId) {
        if (processorUserId == null) {
            throw new IllegalArgumentException("Usuario procesador requerido.");
        }
        User u = userRepository.findById(processorUserId).orElseThrow(
                () -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted()) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        if (u.getRole() == null) {
            throw new IllegalArgumentException("No autorizado.");
        }
        String role = u.getRole().getName();
        if (!"CAJERO".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("No autorizado.");
        }
        return u;
    }

    private static String[] nombresApellidos(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"", ""};
        }
        String t = fullName.trim();
        int sp = t.indexOf(' ');
        if (sp < 0) {
            return new String[]{t, ""};
        }
        return new String[]{t.substring(0, sp), t.substring(sp + 1).trim()};
    }

    private static String dataUrlComprobante(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String mime = "image/jpeg";
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            mime = "image/png";
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private void enviarCorreoPagoValidado(User client, String orderId, BigDecimal total) {
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            return;
        }
        configuracionSistemaRepository.findById("GLOBAL_CONFIG").ifPresent(cfg -> {
            String em = cfg.getEmailSmtp();
            String pw = cfg.getPasswordSmtp();
            if (em == null || em.isBlank() || pw == null || pw.isBlank()) {
                return;
            }
            String negocio = nombreNegocio(cfg);
            String totalStr = total != null ? total.setScale(2, RoundingMode.HALF_UP).toPlainString() : "";
            String asunto = "Pago validado — " + negocio;
            String cuerpo = "Hola,\n\n"
                    + "Tu pago fue procesado correctamente.\n\n"
                    + "Pedido: #" + orderId + "\n"
                    + "Monto: S/ " + totalStr + "\n\n"
                    + "Gracias por tu compra en " + negocio + ".";
            emailService.enviarCorreoTextoPlano(
                    client.getEmail(),
                    asunto,
                    cuerpo,
                    em,
                    pw,
                    client.getId() != null ? client.getId().toString() : null
            );
        });
    }

    private void enviarCorreoPagoRechazado(User client, String orderId, String motivo) {
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            return;
        }
        configuracionSistemaRepository.findById("GLOBAL_CONFIG").ifPresent(cfg -> {
            String em = cfg.getEmailSmtp();
            String pw = cfg.getPasswordSmtp();
            if (em == null || em.isBlank() || pw == null || pw.isBlank()) {
                return;
            }
            String negocio = nombreNegocio(cfg);
            String asunto = "Pago no aceptado — " + negocio;
            String cuerpo = "Hola,\n\n"
                    + "No pudimos aceptar el comprobante de tu pedido #" + orderId + ".\n\n"
                    + "Motivo indicado por caja:\n" + motivo + "\n\n"
                    + "Si crees que es un error, contacta a " + negocio + ".";
            emailService.enviarCorreoTextoPlano(
                    client.getEmail(),
                    asunto,
                    cuerpo,
                    em,
                    pw,
                    client.getId() != null ? client.getId().toString() : null
            );
        });
    }

    private static String nombreNegocio(ConfiguracionSistema cfg) {
        String n = cfg.getNombreNegocio();
        return (n == null || n.isBlank()) ? "Mikunaigen" : n.trim();
    }
}

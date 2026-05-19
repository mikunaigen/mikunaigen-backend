package com.mikunaigen.backend.service;

import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.util.ConfiguracionPlataformaMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SolicitudCambioRolService {

    public static final BigDecimal PRECIO_EMPRENDEDOR = new BigDecimal("20.00");
    public static final BigDecimal PRECIO_NUTRICIONISTA = new BigDecimal("50.00");

    private final SolicitudCambioRolRepository solicitudRepo;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final ConfiguracionGlobalRepository configRepo;
    private final EmailService emailService;
    private final SolicitudPlanPushService planPushService;

    public SolicitudCambioRolService(
            SolicitudCambioRolRepository solicitudRepo,
            UserRepository userRepo,
            RoleRepository roleRepo,
            ConfiguracionGlobalRepository configRepo,
            EmailService emailService,
            SolicitudPlanPushService planPushService
    ) {
        this.solicitudRepo = solicitudRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.configRepo = configRepo;
        this.emailService = emailService;
        this.planPushService = planPushService;
    }

    public Map<String, Object> contextoUsuario(User user) {
        String rolActual = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rolActual", rolActual);
        out.put("puedeSolicitarCambio", puedeSolicitarCambio(rolActual));
        out.put("planesDisponibles", planesDisponiblesPara(rolActual));
        out.put("mediosPago", mediosPagoPublicos());
        solicitudRepo.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente")
                .ifPresentOrElse(
                        s -> out.put("solicitudPendiente", mapSolicitudResumen(s)),
                        () -> out.put("solicitudPendiente", null)
                );
        return out;
    }

    @Transactional
    public ResponseEntity<?> crearSolicitud(User user, String rolSolicitado, String justificacion, MultipartFile comprobante) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        String rolActual = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "");
        if (!puedeSolicitarCambio(rolActual)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tu rol actual no permite solicitar un cambio de plan."));
        }
        if (solicitudRepo.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente").isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Ya tienes una solicitud de cambio de plan en revisión."));
        }

        String rolObjetivo = normalizarRol(rolSolicitado);
        if (!planesDisponiblesPara(rolActual).stream().anyMatch(p -> rolObjetivo.equals(p.get("codigo")))) {
            return ResponseEntity.badRequest().body(Map.of("message", "El plan seleccionado no está disponible para tu rol actual."));
        }

        String just = justificacion != null ? justificacion.trim() : "";
        if (just.length() < 20) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "La justificación debe tener al menos 20 caracteres."));
        }

        if (comprobante == null || comprobante.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes adjuntar el comprobante de pago."));
        }

        String contentType = comprobante.getContentType() != null ? comprobante.getContentType().toLowerCase(Locale.ROOT) : "";
        String filename = comprobante.getOriginalFilename() != null ? comprobante.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        boolean imagenValida = contentType.contains("png") || contentType.contains("jpeg") || contentType.contains("jpg")
                || filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg");
        if (!imagenValida) {
            return ResponseEntity.badRequest().body(Map.of("message", "El comprobante debe ser imagen PNG o JPG."));
        }
        if (comprobante.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "El comprobante no debe superar los 2 MB."));
        }

        Role rol = roleRepo.findByNombre(rolObjetivo).orElse(null);
        if (rol == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Rol solicitado no válido."));
        }

        byte[] bytes;
        try {
            bytes = comprobante.getBytes();
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se pudo leer el comprobante."));
        }

        SolicitudCambioRol solicitud = new SolicitudCambioRol();
        solicitud.setUsuarioId(user.getId());
        solicitud.setRolSolicitadoId(rol.getId());
        solicitud.setJustificacion(just);
        solicitud.setComprobantePagoBytea(bytes);
        solicitud.setEstado("pendiente");
        solicitud.setFechaSolicitud(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        planPushService.notificarSolicitudCreada(user, solicitud);

        return ResponseEntity.ok(Map.of(
                "message",
                "Tu solicitud fue registrada. Tu cuenta será activada dentro de las 24 horas hábiles siguientes a la verificación del pago.",
                "solicitudPendiente", mapSolicitudResumen(solicitud)
        ));
    }

    public List<Map<String, Object>> listarParaAdmin(String estadoFiltro) {
        List<SolicitudCambioRol> lista = estadoFiltro == null || estadoFiltro.isBlank()
                ? solicitudRepo.findAllByOrderByFechaSolicitudDesc()
                : solicitudRepo.findByEstadoIgnoreCaseOrderByFechaSolicitudDesc(estadoFiltro.trim());
        List<Map<String, Object>> out = new ArrayList<>();
        for (SolicitudCambioRol s : lista) {
            out.add(mapSolicitudAdmin(s));
        }
        return out;
    }

    @Transactional
    public ResponseEntity<?> aprobar(Long solicitudId) {
        SolicitudCambioRol solicitud = solicitudRepo.findById(solicitudId).orElse(null);
        if (solicitud == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"pendiente".equalsIgnoreCase(solicitud.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La solicitud ya fue gestionada."));
        }

        User user = userRepo.findById(solicitud.getUsuarioId()).orElse(null);
        Role rol = roleRepo.findById(solicitud.getRolSolicitadoId()).orElse(null);
        if (user == null || rol == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se encontró el usuario o el rol."));
        }

        user.setRole(rol);
        user.setFechaInicioPlan(LocalDateTime.now());
        user.setFechaFinPlan(LocalDateTime.now().plusDays(30));
        user.setActualizadoEn(LocalDateTime.now());
        userRepo.save(user);

        solicitud.setEstado("aprobada");
        solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitudRepo.save(solicitud);

        enviarCorreoPlan(user, "Plan activado en Mikunaigen",
                "Tu solicitud de cambio de plan fue aprobada. Tu nuevo plan " + rol.getNombre()
                        + " está activo hasta el " + user.getFechaFinPlan().toLocalDate() + ".");

        planPushService.notificarSolicitudAprobada(user, solicitud, rol.getNombre());

        return ResponseEntity.ok(Map.of("message", "Solicitud aprobada y rol actualizado."));
    }

    @Transactional
    public ResponseEntity<?> rechazar(Long solicitudId, String motivo) {
        SolicitudCambioRol solicitud = solicitudRepo.findById(solicitudId).orElse(null);
        if (solicitud == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"pendiente".equalsIgnoreCase(solicitud.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La solicitud ya fue gestionada."));
        }
        String motivoFinal = motivo != null ? motivo.trim() : "";
        if (motivoFinal.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes indicar el motivo del rechazo."));
        }

        User user = userRepo.findById(solicitud.getUsuarioId()).orElse(null);
        solicitud.setEstado("rechazada");
        solicitud.setMotivoRechazo(motivoFinal);
        solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitudRepo.save(solicitud);

        if (user != null) {
            enviarCorreoPlan(user, "Solicitud de plan rechazada",
                    "Tu solicitud de cambio de plan fue rechazada.\n\nMotivo: " + motivoFinal);
            planPushService.notificarSolicitudRechazada(user, solicitud, motivoFinal);
        }

        return ResponseEntity.ok(Map.of("message", "Solicitud rechazada."));
    }

    @Transactional
    public void procesarVencimientosYRecordatorios() {
        LocalDate hoy = LocalDate.now();
        List<User> usuarios = userRepo.findAll();
        Role rolEstudiante = roleRepo.findByNombre("estudiante").orElse(null);

        for (User u : usuarios) {
            if (u.getFechaFinPlan() == null || u.isDeleted()) {
                continue;
            }
            LocalDate fin = u.getFechaFinPlan().toLocalDate();
            long dias = ChronoUnit.DAYS.between(hoy, fin);

            if (fin.isBefore(hoy) && rolEstudiante != null) {
                String rol = normalizarRol(u.getRole() != null ? u.getRole().getNombre() : "");
                if ("emprendedor".equals(rol) || "nutricionista".equals(rol)) {
                    u.setRole(rolEstudiante);
                    u.setFechaInicioPlan(null);
                    u.setFechaFinPlan(null);
                    u.setActualizadoEn(LocalDateTime.now());
                    userRepo.save(u);
                    enviarCorreoPlan(u, "Plan vencido - Mikunaigen",
                            "Tu plan ha vencido. Tu cuenta volvió al rol Estudiante. Puedes solicitar un nuevo plan cuando lo desees.");
                }
                continue;
            }

            if (dias == 7 || dias == 3 || dias == 1) {
                enviarCorreoPlan(u, "Recordatorio de vencimiento de plan",
                        "Tu plan vence en " + dias + " día(s) (" + fin + "). Renueva tu suscripción para mantener las funcionalidades.");
            }
        }
    }

    private void enviarCorreoPlan(User user, String asunto, String cuerpo) {
        if (user == null || user.getEmail() == null) {
            return;
        }
        ConfiguracionGlobal config = configRepo.findById(1).orElse(null);
        if (config == null || config.getSmtpEmail() == null || config.getSmtpEmail().isBlank()
                || !"activo".equalsIgnoreCase(config.getSmtpEstado())) {
            return;
        }
        try {
            emailService.enviarCorreoTextoPlano(
                    user.getEmail(),
                    asunto,
                    cuerpo,
                    config.getSmtpEmail(),
                    config.getSmtpContrasenaApp(),
                    user.getId().toString()
            );
        } catch (EmailDispatchException | IllegalStateException ignored) {
        }
    }

    private boolean puedeSolicitarCambio(String rolActual) {
        return "estudiante".equals(rolActual) || "emprendedor".equals(rolActual);
    }

    private List<Map<String, Object>> planesDisponiblesPara(String rolActual) {
        List<Map<String, Object>> planes = new ArrayList<>();
        if ("estudiante".equals(rolActual)) {
            planes.add(planInfo("emprendedor", "Emprendedor", PRECIO_EMPRENDEDOR,
                    "20 inferencias/mes, costos, estacionalidad y exportación Excel."));
            planes.add(planInfo("nutricionista", "Nutricionista", PRECIO_NUTRICIONISTA,
                    "50 inferencias/mes, análisis normativo extendido y exportación Excel/PDF."));
        } else if ("emprendedor".equals(rolActual)) {
            planes.add(planInfo("nutricionista", "Nutricionista", PRECIO_NUTRICIONISTA,
                    "50 inferencias/mes, análisis normativo extendido y exportación Excel/PDF."));
        }
        return planes;
    }

    private Map<String, Object> planInfo(String codigo, String nombre, BigDecimal precio, String beneficios) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("codigo", codigo);
        p.put("nombre", nombre);
        p.put("precio", precio);
        p.put("precioFormateado", "S/ " + precio.toPlainString());
        p.put("beneficios", beneficios);
        return p;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mediosPagoPublicos() {
        ConfiguracionGlobal c = configRepo.findById(1).orElse(null);
        Object medios = ConfiguracionPlataformaMapper.aMapaPublico(c).get("mediosPago");
        if (medios instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private Map<String, Object> mapSolicitudResumen(SolicitudCambioRol s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("estado", s.getEstado());
        m.put("fechaSolicitud", s.getFechaSolicitud());
        roleRepo.findById(s.getRolSolicitadoId()).ifPresent(r -> m.put("rolSolicitado", r.getNombre()));
        return m;
    }

    private Map<String, Object> mapSolicitudAdmin(SolicitudCambioRol s) {
        Map<String, Object> m = new LinkedHashMap<>(mapSolicitudResumen(s));
        userRepo.findById(s.getUsuarioId()).ifPresent(u -> {
            m.put("usuarioEmail", u.getEmail());
            m.put("usuarioNombre", u.getFullName());
        });
        m.put("justificacion", s.getJustificacion());
        m.put("motivoRechazo", s.getMotivoRechazo());
        m.put("fechaRespuesta", s.getFechaRespuesta());
        m.put("tieneComprobante", s.getComprobantePagoBytea() != null && s.getComprobantePagoBytea().length > 0);
        return m;
    }

    public byte[] obtenerComprobante(Long solicitudId) {
        return solicitudRepo.findById(solicitudId)
                .map(SolicitudCambioRol::getComprobantePagoBytea)
                .orElse(null);
    }

    private static String normalizarRol(String rol) {
        if (rol == null) {
            return "";
        }
        String r = rol.trim().toLowerCase(Locale.ROOT);
        if ("cliente".equals(r)) {
            return "estudiante";
        }
        if ("admin".equals(r)) {
            return "administrador";
        }
        return r;
    }
}

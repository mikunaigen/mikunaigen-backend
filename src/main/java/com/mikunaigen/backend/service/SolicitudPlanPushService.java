package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SolicitudPlanPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoleRepository roleRepo;
    private final UserRepository userRepo;

    public SolicitudPlanPushService(
            SimpMessagingTemplate messagingTemplate,
            RoleRepository roleRepo,
            UserRepository userRepo
    ) {
        this.messagingTemplate = messagingTemplate;
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
    }

    public void notificarSolicitudCreada(User user, SolicitudCambioRol solicitud) {
        if (user == null || user.getId() == null || solicitud == null) {
            return;
        }
        Map<String, Object> usuario = baseUsuario("solicitud_creada", user, solicitud);
        usuario.put("message", "Tu solicitud fue registrada y está en revisión.");
        enviarUsuario(user.getId(), usuario);

        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("tipo", "solicitud_nueva");
        admin.put("solicitudId", solicitud.getId());
        admin.put("estado", solicitud.getEstado());
        roleRepo.findById(solicitud.getRolSolicitadoId()).ifPresent(r -> admin.put("rolSolicitado", r.getNombre()));
        userRepo.findById(solicitud.getUsuarioId()).ifPresent(u -> {
            admin.put("usuarioEmail", u.getEmail());
            admin.put("usuarioNombre", u.getFullName());
        });
        enviarAdmin(admin);
    }

    public void notificarSolicitudAprobada(User user, SolicitudCambioRol solicitud, String rolNombre) {
        if (user == null || user.getId() == null || solicitud == null) {
            return;
        }
        Map<String, Object> usuario = baseUsuario("solicitud_aprobada", user, solicitud);
        usuario.put("rolActual", rolNombre);
        usuario.put("solicitudPendiente", null);
        usuario.put("message", "Tu plan fue activado correctamente.");
        enviarUsuario(user.getId(), usuario);

        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("tipo", "solicitud_actualizada");
        admin.put("solicitudId", solicitud.getId());
        admin.put("estado", solicitud.getEstado());
        enviarAdmin(admin);
    }

    public void notificarSolicitudRechazada(User user, SolicitudCambioRol solicitud, String motivo) {
        if (user == null || user.getId() == null || solicitud == null) {
            return;
        }
        Map<String, Object> usuario = baseUsuario("solicitud_rechazada", user, solicitud);
        usuario.put("solicitudPendiente", null);
        usuario.put("motivoRechazo", motivo);
        usuario.put("message", "Tu solicitud de cambio de plan fue rechazada.");
        enviarUsuario(user.getId(), usuario);

        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("tipo", "solicitud_actualizada");
        admin.put("solicitudId", solicitud.getId());
        admin.put("estado", solicitud.getEstado());
        enviarAdmin(admin);
    }

    private Map<String, Object> baseUsuario(String tipo, User user, SolicitudCambioRol solicitud) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tipo", tipo);
        payload.put("solicitudId", solicitud.getId());
        payload.put("usuarioId", user.getId().toString());
        payload.put("estado", solicitud.getEstado());
        roleRepo.findById(solicitud.getRolSolicitadoId()).ifPresent(r -> payload.put("rolSolicitado", r.getNombre()));
        if (user.getRole() != null) {
            payload.put("rolActual", user.getRole().getNombre());
        }
        Map<String, Object> pendiente = new LinkedHashMap<>();
        pendiente.put("id", solicitud.getId());
        pendiente.put("estado", solicitud.getEstado());
        pendiente.put("fechaSolicitud", solicitud.getFechaSolicitud());
        roleRepo.findById(solicitud.getRolSolicitadoId()).ifPresent(r -> pendiente.put("rolSolicitado", r.getNombre()));
        payload.put("solicitudPendiente", pendiente);
        return payload;
    }

    private void enviarUsuario(UUID userId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/planes/usuario/" + userId, (Object) payload);
    }

    private void enviarAdmin(Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/planes/admin", (Object) payload);
    }
}

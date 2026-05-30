package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.BackupAutomatizacion;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.BackupAutomatizacionRepository;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BackupAutomatizacionService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final List<String> FRECUENCIAS = List.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final BackupAutomatizacionRepository repository;
    private final BackupService backupService;
    private final EmailService emailService;
    private final ConfiguracionGlobalRepository configRepository;

    @Value("${app.backup.notify-secret:}")
    private String notifySecret;

    @Value("${app.backup.cron-secret:}")
    private String cronSecret;

    public BackupAutomatizacionService(
            BackupAutomatizacionRepository repository,
            BackupService backupService,
            EmailService emailService,
            ConfiguracionGlobalRepository configRepository) {
        this.repository = repository;
        this.backupService = backupService;
        this.emailService = emailService;
        this.configRepository = configRepository;
    }

    public Map<String, Object> getForAdmin() {
        BackupAutomatizacion estado = cargarOCrear();
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("enabled", estado.isEnabled());
        respuesta.put("frequency", estado.getFrequency());
        respuesta.put("timeHHmm", estado.getTimeHHmm());
        respuesta.put("notifyEmailAfterFinish", estado.isNotifyEmailAfterFinish());
        respuesta.put("nextBackupSummary", resumenProximoBackup(estado));
        respuesta.put("lastAttemptStatus", estado.getLastAttemptStatus() != null ? estado.getLastAttemptStatus() : "NONE");
        respuesta.put("lastAttemptAt", estado.getLastAttemptAt() != null ? estado.getLastAttemptAt().toString() : null);
        respuesta.put("lastWorkflowStatus", estado.getLastWorkflowStatus());
        respuesta.put("lastWorkflowAt", estado.getLastWorkflowAt() != null ? estado.getLastWorkflowAt().toString() : null);
        respuesta.put("lastWorkflowDetail", estado.getLastWorkflowDetail());
        return respuesta;
    }

    public Map<String, Object> saveFromAdmin(Map<String, Object> body) {
        BackupAutomatizacion estado = cargarOCrear();
        Object activo = body.get("enabled");
        if (activo instanceof Boolean b) {
            estado.setEnabled(b);
        }
        String frecuencia = comoTexto(body.get("frequency"));
        if (frecuencia != null && FRECUENCIAS.contains(frecuencia.toUpperCase())) {
            estado.setFrequency(frecuencia.toUpperCase());
        }
        String hora = comoTexto(body.get("timeHHmm"));
        if (hora != null && hora.matches("\\d{2}:\\d{2}")) {
            estado.setTimeHHmm(hora);
        }
        Object notificar = body.get("notifyEmailAfterFinish");
        if (notificar instanceof Boolean b) {
            estado.setNotifyEmailAfterFinish(b);
        }
        estado.setUpdatedAt(Instant.now());
        repository.save(estado);
        return getForAdmin();
    }

    public void recordWorkflowResult(String estadoJob, String detalle) {
        BackupAutomatizacion estado = cargarOCrear();
        String normalizado = normalizarEstadoJob(estadoJob);
        estado.setLastWorkflowStatus(normalizado);
        estado.setLastWorkflowAt(Instant.now());
        estado.setLastWorkflowDetail(detalle);
        String intento;
        if ("SUCCESS".equals(normalizado)) {
            intento = "SUCCESS";
        } else if ("CANCELLED".equals(normalizado)) {
            intento = "CANCELLED";
        } else {
            intento = "FAILURE";
        }
        estado.setLastAttemptStatus(intento);
        estado.setLastAttemptAt(Instant.now());
        repository.save(estado);
        if (estado.isNotifyEmailAfterFinish()) {
            enviarCorreoReporte(estado, normalizado, detalle);
        }
    }

    public void runScheduledIfDue() {
        BackupAutomatizacion estado = cargarOCrear();
        if (!estado.isEnabled()) {
            return;
        }
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        LocalTime horaDeseada;
        try {
            horaDeseada = LocalTime.parse(estado.getTimeHHmm());
        } catch (Exception e) {
            return;
        }
        if (ahora.getHour() != horaDeseada.getHour() || ahora.getMinute() != horaDeseada.getMinute()) {
            return;
        }
        if (!coincideCalendarioFrecuencia(ahora, estado.getFrequency())) {
            return;
        }
        String slot = claveSlot(ahora, estado.getFrequency());
        if (slot.equals(estado.getLastFiredSlotKey())) {
            return;
        }
        try {
            backupService.generatePairedBackups();
            estado.setLastFiredSlotKey(slot);
            estado.setLastAttemptAt(Instant.now());
            estado.setLastAttemptStatus("PENDING");
            repository.save(estado);
        } catch (Exception ex) {
            estado.setLastAttemptAt(Instant.now());
            estado.setLastAttemptStatus("FAILURE");
            estado.setLastWorkflowStatus("FAILURE");
            estado.setLastWorkflowAt(Instant.now());
            estado.setLastWorkflowDetail(ex.getMessage());
            repository.save(estado);
        }
    }

    public void runFromExternalCron(String secretoProvisto) {
        if (cronSecret == null || cronSecret.isBlank() || !cronSecret.equals(secretoProvisto)) {
            throw new IllegalArgumentException("No autorizado.");
        }
        runScheduledIfDue();
    }

    public boolean verifyNotifySecret(String firma) {
        return notifySecret != null && !notifySecret.isBlank() && notifySecret.equals(firma);
    }

    private BackupAutomatizacion cargarOCrear() {
        Optional<BackupAutomatizacion> existente = repository.findById(BackupAutomatizacion.SINGLETON_ID);
        if (existente.isPresent()) {
            return existente.get();
        }
        BackupAutomatizacion nuevo = new BackupAutomatizacion();
        nuevo.setId(BackupAutomatizacion.SINGLETON_ID);
        return nuevo;
    }

    private static String comoTexto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private String resumenProximoBackup(BackupAutomatizacion estado) {
        if (!estado.isEnabled()) {
            return "Copia automática desactivada.";
        }
        LocalTime hora;
        try {
            hora = LocalTime.parse(estado.getTimeHHmm());
        } catch (Exception e) {
            return "Configura una hora válida.";
        }
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        ZonedDateTime proximo = switch (estado.getFrequency()) {
            case "WEEKLY" -> proximoLunes(ahora, hora);
            case "MONTHLY" -> proximoPrimeroMes(ahora, hora);
            default -> proximoDiario(ahora, hora);
        };
        return formatearProximoHumano(proximo, hora);
    }

    private ZonedDateTime proximoDiario(ZonedDateTime ahora, LocalTime hora) {
        ZonedDateTime candidato = ahora.with(hora);
        if (!candidato.isAfter(ahora)) {
            candidato = candidato.plusDays(1);
        }
        return candidato;
    }

    private ZonedDateTime proximoLunes(ZonedDateTime ahora, LocalTime hora) {
        LocalDate dia = ahora.toLocalDate();
        for (int i = 0; i < 370; i++) {
            LocalDate intento = dia.plusDays(i);
            if (intento.getDayOfWeek() != DayOfWeek.MONDAY) {
                continue;
            }
            ZonedDateTime z = ZonedDateTime.of(intento, hora, ZONA);
            if (z.isAfter(ahora)) {
                return z;
            }
        }
        return ahora.plusDays(7).with(hora);
    }

    private ZonedDateTime proximoPrimeroMes(ZonedDateTime ahora, LocalTime hora) {
        LocalDate dia = ahora.toLocalDate();
        LocalDate primero = dia.withDayOfMonth(1);
        ZonedDateTime candidato = ZonedDateTime.of(primero, hora, ZONA);
        if (!candidato.isAfter(ahora)) {
            primero = primero.plusMonths(1).withDayOfMonth(1);
            candidato = ZonedDateTime.of(primero, hora, ZONA);
        }
        return candidato;
    }

    private String formatearProximoHumano(ZonedDateTime cuando, LocalTime hora) {
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        LocalDate hoy = ahora.toLocalDate();
        LocalDate dia = cuando.toLocalDate();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("hh:mm a", ES);
        String parteHora = cuando.format(hm);
        if (dia.equals(hoy)) {
            return "Programado para hoy a las " + parteHora + ".";
        }
        if (dia.equals(hoy.plusDays(1))) {
            return "Programado para mañana a las " + parteHora + ".";
        }
        String diaSemana = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ES);
        String parteFecha = dia.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES));
        return "Programado para el " + diaSemana + " " + parteFecha + " a las " + parteHora + ".";
    }

    private boolean coincideCalendarioFrecuencia(ZonedDateTime ahora, String frecuencia) {
        String f = frecuencia != null ? frecuencia : "DAILY";
        return switch (f) {
            case "WEEKLY" -> ahora.getDayOfWeek() == DayOfWeek.MONDAY;
            case "MONTHLY" -> ahora.getDayOfMonth() == 1;
            default -> true;
        };
    }

    private String claveSlot(ZonedDateTime ahora, String frecuencia) {
        String f = frecuencia != null ? frecuencia : "DAILY";
        LocalDate dia = ahora.toLocalDate();
        return switch (f) {
            case "WEEKLY" -> {
                WeekFields wf = WeekFields.ISO;
                int anio = dia.get(wf.weekBasedYear());
                int semana = dia.get(wf.weekOfWeekBasedYear());
                yield String.format("W:%d-%02d", anio, semana);
            }
            case "MONTHLY" -> "M:" + dia.getYear() + "-" + String.format("%02d", dia.getMonthValue());
            default -> "D:" + dia;
        };
    }

    private static String normalizarEstadoJob(String raw) {
        if (raw == null) {
            return "UNKNOWN";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("success")) {
            return "SUCCESS";
        }
        if (s.contains("fail")) {
            return "FAILURE";
        }
        if (s.contains("cancel")) {
            return "CANCELLED";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private void enviarCorreoReporte(BackupAutomatizacion estado, String estadoWorkflow, String detalle) {
        Optional<ConfiguracionGlobal> cfg = configRepository.findById(1);
        if (cfg.isEmpty()) {
            return;
        }
        ConfiguracionGlobal c = cfg.get();
        String emisor = c.getSmtpEmail();
        String clave = c.getSmtpContrasenaApp();
        if (emisor == null || emisor.isBlank() || clave == null || clave.isBlank()) {
            return;
        }
        String nombre = c.getNombrePlataforma();
        String plataforma = (nombre == null || nombre.isBlank()) ? "Mikunaigen" : nombre.trim();
        boolean ok = "SUCCESS".equals(estadoWorkflow);
        String asunto = (ok ? "Respaldo automático exitoso — " : "Respaldo automático con incidencias — ") + plataforma;
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Estado del workflow en GitHub Actions: ").append(estadoWorkflow).append("\n");
        cuerpo.append("Automático: ").append(estado.isEnabled() ? "activo" : "inactivo").append("\n");
        cuerpo.append("Frecuencia: ").append(estado.getFrequency()).append("\n");
        if (detalle != null && !detalle.isBlank()) {
            cuerpo.append("Detalle: ").append(detalle).append("\n");
        }
        try {
            emailService.enviarCorreoTextoPlano(emisor, asunto, cuerpo.toString(), emisor, clave, null);
        } catch (Exception ignored) {
        }
    }
}

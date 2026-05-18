package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.nosql.BackupAutomatizacion;
import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.repository.nosql.BackupAutomatizacionRepository;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
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

    private static final ZoneId ZONE = ZoneId.of("America/Lima");
    private static final List<String> FREQ = List.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final BackupAutomatizacionRepository repository;
    private final BackupService backupService;
    private final EmailService emailService;
    private final ConfiguracionSistemaRepository configRepository;

    @Value("${app.backup.notify-secret:}")
    private String notifySecret;

    @Value("${app.backup.cron-secret:}")
    private String cronSecret;

    public BackupAutomatizacionService(
            BackupAutomatizacionRepository repository,
            BackupService backupService,
            EmailService emailService,
            ConfiguracionSistemaRepository configRepository) {
        this.repository = repository;
        this.backupService = backupService;
        this.emailService = emailService;
        this.configRepository = configRepository;
    }

    public Map<String, Object> getForAdmin() {
        BackupAutomatizacion s = loadOrDefault();
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", s.isEnabled());
        m.put("frequency", s.getFrequency());
        m.put("timeHHmm", s.getTimeHHmm());
        m.put("notifyEmailAfterFinish", s.isNotifyEmailAfterFinish());
        m.put("nextBackupSummary", buildNextSummary(s));
        m.put("lastAttemptStatus", s.getLastAttemptStatus() != null ? s.getLastAttemptStatus() : "NONE");
        m.put("lastAttemptAt", s.getLastAttemptAt() != null ? s.getLastAttemptAt().toString() : null);
        m.put("lastWorkflowStatus", s.getLastWorkflowStatus());
        m.put("lastWorkflowAt", s.getLastWorkflowAt() != null ? s.getLastWorkflowAt().toString() : null);
        m.put("lastWorkflowDetail", s.getLastWorkflowDetail());
        return m;
    }

    public Map<String, Object> saveFromAdmin(Map<String, Object> body) {
        BackupAutomatizacion s = loadOrDefault();
        Object en = body.get("enabled");
        if (en instanceof Boolean b) {
            s.setEnabled(b);
        }
        String f = asString(body.get("frequency"));
        if (f != null && FREQ.contains(f.toUpperCase())) {
            s.setFrequency(f.toUpperCase());
        }
        String t = asString(body.get("timeHHmm"));
        if (t != null && t.matches("\\d{2}:\\d{2}")) {
            s.setTimeHHmm(t);
        }
        Object n = body.get("notifyEmailAfterFinish");
        if (n instanceof Boolean b) {
            s.setNotifyEmailAfterFinish(b);
        }
        s.setUpdatedAt(Instant.now());
        repository.save(s);
        return getForAdmin();
    }

    public void recordWorkflowResult(String jobStatus, String detail) {
        BackupAutomatizacion s = loadOrDefault();
        String norm = normalizeJobStatus(jobStatus);
        s.setLastWorkflowStatus(norm);
        s.setLastWorkflowAt(Instant.now());
        s.setLastWorkflowDetail(detail);
        String attempt;
        if ("SUCCESS".equals(norm)) {
            attempt = "SUCCESS";
        } else if ("CANCELLED".equals(norm)) {
            attempt = "CANCELLED";
        } else {
            attempt = "FAILURE";
        }
        s.setLastAttemptStatus(attempt);
        s.setLastAttemptAt(Instant.now());
        repository.save(s);
        if (s.isNotifyEmailAfterFinish()) {
            sendReportEmail(s, norm, detail);
        }
    }

    public void runScheduledIfDue() {
        BackupAutomatizacion s = loadOrDefault();
        if (!s.isEnabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalTime want;
        try {
            want = LocalTime.parse(s.getTimeHHmm());
        } catch (Exception e) {
            return;
        }
        if (now.getHour() != want.getHour() || now.getMinute() != want.getMinute()) {
            return;
        }
        if (!matchesFrequencyCalendar(now, s.getFrequency())) {
            return;
        }
        String slot = computeSlotKey(now, s.getFrequency());
        if (slot.equals(s.getLastFiredSlotKey())) {
            return;
        }
        try {
            backupService.generatePairedBackups();
            s.setLastFiredSlotKey(slot);
            s.setLastAttemptAt(Instant.now());
            s.setLastAttemptStatus("PENDING");
            repository.save(s);
        } catch (Exception ex) {
            s.setLastAttemptAt(Instant.now());
            s.setLastAttemptStatus("FAILURE");
            s.setLastWorkflowStatus("FAILURE");
            s.setLastWorkflowAt(Instant.now());
            s.setLastWorkflowDetail(ex.getMessage());
            repository.save(s);
        }
    }

    public void runFromExternalCron(String providedSecret) {
        if (cronSecret == null || cronSecret.isBlank() || !cronSecret.equals(providedSecret)) {
            throw new IllegalArgumentException("No autorizado.");
        }
        runScheduledIfDue();
    }

    public boolean verifyNotifySecret(String header) {
        return notifySecret != null && !notifySecret.isBlank() && notifySecret.equals(header);
    }

    private BackupAutomatizacion loadOrDefault() {
        Optional<BackupAutomatizacion> o = repository.findById(BackupAutomatizacion.SINGLETON_ID);
        if (o.isPresent()) {
            return o.get();
        }
        BackupAutomatizacion d = new BackupAutomatizacion();
        d.setId(BackupAutomatizacion.SINGLETON_ID);
        return d;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String buildNextSummary(BackupAutomatizacion s) {
        if (!s.isEnabled()) {
            return "Copia automática desactivada.";
        }
        LocalTime t;
        try {
            t = LocalTime.parse(s.getTimeHHmm());
        } catch (Exception e) {
            return "Configura una hora válida.";
        }
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = switch (s.getFrequency()) {
            case "WEEKLY" -> nextWeeklyMonday(now, t);
            case "MONTHLY" -> nextMonthlyFirst(now, t);
            default -> nextDaily(now, t);
        };
        return formatHumanNext(next, t);
    }

    private ZonedDateTime nextDaily(ZonedDateTime now, LocalTime t) {
        ZonedDateTime cand = now.with(t);
        if (!cand.isAfter(now)) {
            cand = cand.plusDays(1);
        }
        return cand;
    }

    private ZonedDateTime nextWeeklyMonday(ZonedDateTime now, LocalTime t) {
        LocalDate d = now.toLocalDate();
        for (int i = 0; i < 370; i++) {
            LocalDate tryD = d.plusDays(i);
            if (tryD.getDayOfWeek() != DayOfWeek.MONDAY) {
                continue;
            }
            ZonedDateTime z = ZonedDateTime.of(tryD, t, ZONE);
            if (z.isAfter(now)) {
                return z;
            }
        }
        return now.plusDays(7).with(t);
    }

    private ZonedDateTime nextMonthlyFirst(ZonedDateTime now, LocalTime t) {
        LocalDate d = now.toLocalDate();
        LocalDate first = d.withDayOfMonth(1);
        ZonedDateTime cand = ZonedDateTime.of(first, t, ZONE);
        if (!cand.isAfter(now)) {
            first = first.plusMonths(1).withDayOfMonth(1);
            cand = ZonedDateTime.of(first, t, ZONE);
        }
        return cand;
    }

    private String formatHumanNext(ZonedDateTime when, LocalTime t) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate day = when.toLocalDate();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("hh:mm a", ES);
        String hourPart = when.format(hm);

        if (day.equals(today)) {
            return "Programado para hoy a las " + hourPart + ".";
        }
        if (day.equals(today.plusDays(1))) {
            return "Programado para mañana a las " + hourPart + ".";
        }
        String dow = day.getDayOfWeek().getDisplayName(TextStyle.FULL, ES);
        String datePart = day.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES));
        return "Programado para el " + dow + " " + datePart + " a las " + hourPart + ".";
    }

    private boolean matchesFrequencyCalendar(ZonedDateTime now, String frequency) {
        String f = frequency != null ? frequency : "DAILY";
        return switch (f) {
            case "WEEKLY" -> now.getDayOfWeek() == DayOfWeek.MONDAY;
            case "MONTHLY" -> now.getDayOfMonth() == 1;
            default -> true;
        };
    }

    private String computeSlotKey(ZonedDateTime now, String frequency) {
        String f = frequency != null ? frequency : "DAILY";
        LocalDate d = now.toLocalDate();
        return switch (f) {
            case "WEEKLY" -> {
                WeekFields wf = WeekFields.ISO;
                int y = d.get(wf.weekBasedYear());
                int w = d.get(wf.weekOfWeekBasedYear());
                yield String.format("W:%d-%02d", y, w);
            }
            case "MONTHLY" -> {
                yield "M:" + d.getYear() + "-" + String.format("%02d", d.getMonthValue());
            }
            default -> "D:" + d;
        };
    }

    private static String normalizeJobStatus(String raw) {
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

    private void sendReportEmail(BackupAutomatizacion state, String workflowNorm, String detail) {
        Optional<ConfiguracionSistema> cfg = configRepository.findById("GLOBAL_CONFIG");
        if (cfg.isEmpty()) {
            return;
        }
        ConfiguracionSistema c = cfg.get();
        String em = c.getEmailSmtp();
        String pw = c.getPasswordSmtp();
        if (em == null || em.isBlank() || pw == null || pw.isBlank()) {
            return;
        }
        String negocio = c.getNombreNegocio();
        String nb = (negocio == null || negocio.isBlank()) ? "Mikunaigen" : negocio.trim();
        boolean ok = "SUCCESS".equals(workflowNorm);
        String asunto = (ok ? "Respaldo automático exitoso — " : "Respaldo automático con incidencias — ") + nb;
        StringBuilder body = new StringBuilder();
        body.append("Estado del workflow en GitHub Actions: ").append(workflowNorm).append("\n");
        body.append("Automático: ").append(state.isEnabled() ? "activo" : "inactivo").append("\n");
        body.append("Frecuencia: ").append(state.getFrequency()).append("\n");
        if (detail != null && !detail.isBlank()) {
            body.append("Detalle: ").append(detail).append("\n");
        }
        try {
            emailService.enviarCorreoTextoPlano(em, asunto, body.toString(), em, pw, null);
        } catch (Exception ignored) {
        }
    }
}

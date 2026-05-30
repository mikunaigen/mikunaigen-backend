package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.FrontendErrorReportRequest;
import com.mikunaigen.backend.model.sql.RegistroError;
import com.mikunaigen.backend.repository.sql.RegistroErrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

@Service
public class RegistroErrorService {

    private final RegistroErrorRepository repository;

    public RegistroErrorService(RegistroErrorRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrarErrorBackend(Exception ex, String rutaUrl, UUID usuarioId) {
        if (ex == null) {
            return;
        }
        RegistroError registro = new RegistroError();
        registro.setOrigen("backend");
        registro.setMensaje(truncar(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), 4000));
        registro.setPilaError(truncar(stackTrace(ex), 8000));
        registro.setRutaUrl(truncar(rutaUrl, 255));
        registro.setUsuarioId(usuarioId);
        repository.save(registro);
    }

    @Transactional
    public void registrarErrorFrontend(FrontendErrorReportRequest solicitud) {
        if (solicitud == null || solicitud.message() == null || solicitud.message().isBlank()) {
            return;
        }
        RegistroError registro = new RegistroError();
        registro.setOrigen("frontend");
        registro.setMensaje(truncar(solicitud.message(), 4000));
        registro.setPilaError(truncar(solicitud.stack(), 8000));
        String ruta = solicitud.routeUrl() != null && !solicitud.routeUrl().isBlank()
                ? solicitud.routeUrl()
                : solicitud.pageUrl();
        registro.setRutaUrl(truncar(ruta, 255));
        registro.setUsuarioId(parseUuid(solicitud.userId()));
        repository.save(registro);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String stackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}

package com.mikunaigen.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ContextoInteligenciaService {

    public record ContextoInteligencia(
            Double temp,
            String condition,
            String day,
            String segment
    ) {}

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=-12.0686&longitude=-75.2102&current_weather=true";

    public ContextoInteligencia contextoActual() {
        LocalDateTime now = LocalDateTime.now();
        Double temp = null;
        String condition = "DESCONOCIDO";

        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> response = restTemplate.getForObject(OPEN_METEO_URL, Map.class);
            
            if (response != null && response.containsKey("current_weather")) {
                Map<String, Object> cw = (Map<String, Object>) response.get("current_weather");
                
                if (cw.get("temperature") != null) {
                    temp = Double.valueOf(cw.get("temperature").toString());
                }
                
                int weatherCode = cw.get("weathercode") != null ? (int) cw.get("weathercode") : -1;
                condition = mapWeatherCode(weatherCode);
            }
        } catch (Exception e) {
            System.err.println("Error consultando clima: " + e.getMessage());
        }

        return new ContextoInteligencia(
                temp,
                condition,
                mapDay(now.getDayOfWeek()),
                mapSegment(now.getHour())
        );
    }

    private String mapSegment(int hora) {
        if (hora >= 0 && hora < 6) return "MADRUGADA";
        if (hora >= 6 && hora < 12) return "MAÑANA";
        if (hora >= 12 && hora < 18) return "TARDE";
        return "NOCHE";
    }

    private String mapDay(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private String mapWeatherCode(int code) {
        if (code < 0) return "DESCONOCIDO";
        if (code == 0) return "SOLEADO";
        if (code == 1 || code == 2) return "PARCIALMENTE_NUBLADO";
        if (code == 3 || code == 45 || code == 48) return "NUBLADO";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "LLUVIOSO";
        if (code >= 95) return "TORMENTA";
        return "OTRO";
    }
}
package com.mikunaigen.backend.config;

import com.mikunaigen.backend.service.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceFilter(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!maintenanceModeService.isMaintenance() || isAllowedDuringMaintenance(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(503);
        response.setHeader("X-Maintenance", "true");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"MAINTENANCE\"}");
    }

    private boolean isAllowedDuringMaintenance(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/webhooks/");
    }
}


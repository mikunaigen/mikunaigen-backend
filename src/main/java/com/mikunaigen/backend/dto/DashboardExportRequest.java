package com.mikunaigen.backend.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardExportRequest {

    private String tab;
    private String format;
    private boolean includeKpis = true;
    private boolean includeCharts = true;
    private boolean includeTables = true;
    private Map<String, Object> filters = new LinkedHashMap<>();
}

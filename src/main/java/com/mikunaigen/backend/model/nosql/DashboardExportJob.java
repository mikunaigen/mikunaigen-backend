package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "dashboard_export_jobs")
public class DashboardExportJob {

    @Id
    private String id;

    private String tab;
    private String tabLabel;
    private String format;
    private String fileName;
    private String backupKey;
    private String exportToken;
    private String status;
    private String downloadUrl;
    private String errorMessage;
    private String filtersJson;
    private String generatedBy;
    private boolean includeKpis;
    private boolean includeCharts;
    private boolean includeTables;
    private Instant createdAt;
    private Instant updatedAt;
}

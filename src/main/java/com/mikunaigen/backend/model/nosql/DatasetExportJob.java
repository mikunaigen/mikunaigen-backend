package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "dataset_export_jobs")
public class DatasetExportJob {

    @Id
    private String id;

    private int slot;
    private String fileName;
    private String backupKey;
    private String status;
    private String downloadUrl;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}

package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.DashboardExportJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DashboardExportJobRepository extends MongoRepository<DashboardExportJob, String> {

    Optional<DashboardExportJob> findByBackupKey(String backupKey);
}

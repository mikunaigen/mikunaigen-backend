package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.DatasetExportJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DatasetExportJobRepository extends MongoRepository<DatasetExportJob, String> {

    Optional<DatasetExportJob> findByBackupKey(String backupKey);
}

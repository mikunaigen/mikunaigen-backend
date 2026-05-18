package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.BackupAutomatizacion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BackupAutomatizacionRepository extends MongoRepository<BackupAutomatizacion, String> {
}

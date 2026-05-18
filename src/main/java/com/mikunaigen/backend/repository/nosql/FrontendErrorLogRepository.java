package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.FrontendErrorLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FrontendErrorLogRepository extends MongoRepository<FrontendErrorLog, String> {
}

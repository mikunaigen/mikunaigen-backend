package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.EmailDispatchLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailDispatchLogRepository extends MongoRepository<EmailDispatchLog, String> {
}

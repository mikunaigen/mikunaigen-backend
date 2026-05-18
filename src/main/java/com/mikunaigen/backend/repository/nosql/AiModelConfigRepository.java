package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.AiModelConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiModelConfigRepository extends MongoRepository<AiModelConfig, String> {
}

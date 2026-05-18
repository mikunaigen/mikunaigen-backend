package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.UserInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {
    List<UserInteraction> findTop100ByUserIdOrderByTimestampDesc(String userId);

    List<UserInteraction> findByTimestampBetween(LocalDateTime from, LocalDateTime to);
}

package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "user_interactions")
public class UserInteraction {
    @Id
    private String id;

    private String userId;
    private String productId;
    private String action;
    private Integer dwellTimeSeconds;
    private InteractionContext context;
    private LocalDateTime timestamp = LocalDateTime.now();

    @Data
    public static class InteractionContext {
        private Double temp;
        private String condition;
        private String day;
        private String segment;
    }
}

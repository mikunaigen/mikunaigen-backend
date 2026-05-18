package com.mikunaigen.backend.repository.nosql;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
public class UserInteractionAnalyticsSupport {

    private static final String COLLECTION = "user_interactions";

    private final MongoTemplate mongoTemplate;

    public UserInteractionAnalyticsSupport(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<LocalDateTime> findMinTimestamp() {
        Document doc = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("timestamp", new Document("$exists", true)))
                .projection(new Document("timestamp", 1))
                .sort(new Document("timestamp", 1))
                .limit(1)
                .first();
        if (doc == null) {
            return Optional.empty();
        }
        Object ts = doc.get("timestamp");
        if (ts instanceof LocalDateTime ldt) {
            return Optional.of(ldt);
        }
        if (ts instanceof Date d) {
            return Optional.of(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
        }
        return Optional.empty();
    }

    public InteractionAgg aggregate(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String action,
            String condicionClima,
            String segmento,
            String userId
    ) {
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("timestamp").gte(from).lt(toExclusive));
        if (action != null && !action.isBlank()) {
            parts.add(Criteria.where("action").regex("^" + Pattern.quote(action.trim()) + "$", "i"));
        }
        if (userId != null && !userId.isBlank()) {
            parts.add(Criteria.where("userId").is(userId.trim()));
        }
        if (condicionClima != null && !condicionClima.isBlank()) {
            parts.add(Criteria.where("context.condition").regex("^" + Pattern.quote(condicionClima.trim()) + "$", "i"));
        }
        if (segmento != null && !segmento.isBlank()) {
            parts.add(Criteria.where("context.segment").regex("^" + Pattern.quote(segmento.trim()) + "$", "i"));
        }
        Criteria criteria = new Criteria().andOperator(parts.toArray(Criteria[]::new));

        Document facet = new Document();
        facet.append("byAction", List.of(
                new Document("$group", new Document("_id", "$action").append("c", new Document("$sum", 1)))
        ));
        facet.append("topProducts", List.of(
                new Document("$match", new Document("$and", Arrays.asList(
                        new Document("productId", new Document("$exists", true)),
                        new Document("productId", new Document("$ne", ""))
                ))),
                new Document("$group", new Document("_id", "$productId").append("c", new Document("$sum", 1))),
                new Document("$sort", new Document("c", -1)),
                new Document("$limit", 10)
        ));
        facet.append("dwell", List.of(
                new Document("$group", new Document("_id", (Object) null).append("a", new Document("$avg", "$dwellTimeSeconds")))
        ));
        facet.append("climaAccion", List.of(
                new Document("$group", new Document("_id",
                        new Document("cond", "$context.condition").append("act", "$action"))
                        .append("c", new Document("$sum", 1)))
        ));
        facet.append("bySegment", List.of(
                new Document("$group", new Document("_id", "$context.segment").append("c", new Document("$sum", 1)))
        ));
        facet.append("totalCount", List.of(
                new Document("$count", "t")
        ));

        AggregationOperation facetOp = context -> new Document("$facet", facet);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                facetOp
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
        Document root = results.getUniqueMappedResult();
        if (root == null) {
            return InteractionAgg.empty();
        }

        long total = 0L;
        List<Document> totalList = root.getList("totalCount", Document.class);
        if (totalList != null && !totalList.isEmpty()) {
            Document td = totalList.get(0);
            Number tn = td.get("t", Number.class);
            if (tn != null) {
                total = tn.longValue();
            }
        }

        Map<String, Long> porAccion = new HashMap<>();
        List<Document> byAction = root.getList("byAction", Document.class);
        if (byAction != null) {
            for (Document d : byAction) {
                if (d != null) {
                    Object id = d.get("_id");
                    String key = id == null ? "?" : String.valueOf(id);
                    Number n = d.get("c", Number.class);
                    porAccion.merge(key, n == null ? 0L : n.longValue(), Long::sum);
                }
            }
        }

        List<Map.Entry<String, Long>> topOrdered = new ArrayList<>();
        List<Document> topProducts = root.getList("topProducts", Document.class);
        if (topProducts != null) {
            for (Document d : topProducts) {
                if (d != null) {
                    Object id = d.get("_id");
                    if (id == null) {
                        continue;
                    }
                    String pid = String.valueOf(id);
                    Number n = d.get("c", Number.class);
                    topOrdered.add(Map.entry(pid, n == null ? 0L : n.longValue()));
                }
            }
        }

        double dwellAvg = 0;
        List<Document> dwellList = root.getList("dwell", Document.class);
        if (dwellList != null && !dwellList.isEmpty()) {
            Document dd = dwellList.get(0);
            Number an = dd.get("a", Number.class);
            if (an != null) {
                dwellAvg = an.doubleValue();
            }
        }

        Map<String, Map<String, Long>> climaPorAccion = new LinkedHashMap<>();
        List<Document> caList = root.getList("climaAccion", Document.class);
        if (caList != null) {
            for (Document d : caList) {
                if (d != null) {
                    Object ido = d.get("_id");
                    Number cn = d.get("c", Number.class);
                    long cv = cn == null ? 0L : cn.longValue();
                    String cond = "—";
                    String act = "?";
                    if (ido instanceof Document iddoc) {
                        Object c0 = iddoc.get("cond");
                        Object a0 = iddoc.get("act");
                        cond = c0 != null ? String.valueOf(c0) : "—";
                        act = a0 != null ? String.valueOf(a0) : "?";
                    }
                    climaPorAccion.computeIfAbsent(cond, k -> new LinkedHashMap<>()).merge(act, cv, Long::sum);
                }
            }
        }

        Map<String, Long> porSegmento = new LinkedHashMap<>();
        List<Document> segList = root.getList("bySegment", Document.class);
        if (segList != null) {
            for (Document d : segList) {
                if (d != null) {
                    Object id = d.get("_id");
                    String key = id == null ? "—" : String.valueOf(id);
                    Number n = d.get("c", Number.class);
                    porSegmento.merge(key, n == null ? 0L : n.longValue(), Long::sum);
                }
            }
        }

        return new InteractionAgg(total, porAccion, topOrdered, dwellAvg, climaPorAccion, porSegmento);
    }

    public record InteractionAgg(
            long total,
            Map<String, Long> porAccion,
            List<Map.Entry<String, Long>> topProductosOrdered,
            double dwellAvg,
            Map<String, Map<String, Long>> climaPorAccion,
            Map<String, Long> porSegmento
    ) {
        static InteractionAgg empty() {
            return new InteractionAgg(0L, Map.of(), List.of(), 0.0, Map.of(), Map.of());
        }
    }
}

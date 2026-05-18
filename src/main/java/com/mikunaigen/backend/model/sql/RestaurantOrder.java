package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "orders")
public class RestaurantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, columnDefinition = "uuid")
    private User client;

    @Column(length = 50, nullable = false)
    private String status = "VALIDANDO_PAGO";

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "payment_receipt_image", columnDefinition = "bytea")
    private byte[] paymentReceiptImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by", columnDefinition = "uuid")
    private User processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_person_id", columnDefinition = "uuid")
    private User deliveryPerson;

    @Column(name = "delivery_assigned_at")
    private LocalDateTime deliveryAssignedAt;

    @Column(name = "delivery_proof_image", columnDefinition = "bytea")
    private byte[] deliveryProofImage;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "is_rated", nullable = false)
    private Boolean isRated = false;

    @Column(name = "weather_temp_c")
    private Double weatherTempC;

    @Column(name = "weather_condition", length = 50)
    private String weatherCondition;

    @Column(name = "moment_of_day", length = 20)
    private String momentOfDay;

    @Column(name = "day_of_week", length = 20)
    private String dayOfWeek;
}

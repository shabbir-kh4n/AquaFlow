package com.shabbir.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alert_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartment_id", nullable = false, unique = true)
    private Apartment apartment;

    @Column(name = "monthly_threshold_kl", precision = 12, scale = 3)
    private BigDecimal monthlyThresholdKl;

    @Column(name = "outlier_std_dev_multiplier", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal outlierStdDevMultiplier = new BigDecimal("2.0");

    @Column(name = "threshold_alerts_enabled", nullable = false)
    @Builder.Default
    private boolean thresholdAlertsEnabled = true;

    @Column(name = "outlier_alerts_enabled", nullable = false)
    @Builder.Default
    private boolean outlierAlertsEnabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

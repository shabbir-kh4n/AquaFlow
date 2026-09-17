package com.hydroledger.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tariff_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TariffPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartment_id", nullable = false, unique = true)
    private Apartment apartment;

    @OneToMany(mappedBy = "tariffPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("tierOrder ASC")
    @Builder.Default
    private List<TariffTier> tiers = new ArrayList<>();

    // Flat rate applied to the household's ENTIRE consumption every month, on top of (not instead
    // of) whatever the tariff tiers below add for usage that falls into their slabs.
    @Column(name = "base_price_per_unit", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal basePricePerUnit = BigDecimal.ZERO;

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

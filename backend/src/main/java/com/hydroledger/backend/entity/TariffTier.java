package com.hydroledger.backend.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tariff_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TariffTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_plan_id", nullable = false)
    private TariffPlan tariffPlan;

    @Column(name = "tier_order", nullable = false)
    private Integer tierOrder;

    @Column(name = "min_units", nullable = false, precision = 12, scale = 3)
    private BigDecimal minUnits;

    @Column(name = "max_units", precision = 12, scale = 3)
    private BigDecimal maxUnits;

    @Column(name = "rate_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerUnit;
}

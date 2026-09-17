package com.hydroledger.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "consumption_units", nullable = false, precision = 12, scale = 3)
    private BigDecimal consumptionUnits;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "tariff_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tariffAmount = BigDecimal.ZERO;

    @Column(name = "common_area_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal commonAreaAmount = BigDecimal.ZERO;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean paid = false;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("tierOrder ASC")
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceCommonAreaCharge> commonAreaCharges = new ArrayList<>();
}

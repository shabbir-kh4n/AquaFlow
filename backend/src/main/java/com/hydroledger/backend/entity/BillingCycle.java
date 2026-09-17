package com.hydroledger.backend.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "billing_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingCycleStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "billingCycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();
}

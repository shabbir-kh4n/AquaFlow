package com.shabbir.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.BillingCycleDetailResponse;
import com.shabbir.backend.dto.BillingCycleResponse;
import com.shabbir.backend.dto.CommonAreaChargeLine;
import com.shabbir.backend.dto.CommonAreaConfigResponse;
import com.shabbir.backend.dto.InvoiceResponse;
import com.shabbir.backend.dto.TariffPlanResponse;
import com.shabbir.backend.dto.TierChargeLine;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BillingCycle;
import com.shabbir.backend.entity.BillingCycleStatus;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.Invoice;
import com.shabbir.backend.entity.InvoiceCommonAreaCharge;
import com.shabbir.backend.entity.InvoiceLineItem;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.BillingCycleRepository;
import com.shabbir.backend.repository.HouseholdRepository;
import com.shabbir.backend.repository.InvoiceRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.WaterMeterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final BillingCycleRepository billingCycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final HouseholdRepository householdRepository;
    private final ResidentRepository residentRepository;
    private final WaterMeterRepository waterMeterRepository;
    private final WaterUsageReadingService waterUsageReadingService;
    private final TariffPlanService tariffPlanService;
    private final CommonAreaConfigService commonAreaConfigService;

    @Transactional
    public BillingCycleResponse open(Apartment apartment, YearMonth month) {
        String periodMonth = month.toString();
        if (billingCycleRepository.existsByApartmentAndPeriodMonth(apartment, periodMonth)) {
            throw new ApiException(HttpStatus.CONFLICT, "A billing cycle already exists for " + periodMonth);
        }

        BillingCycle cycle = BillingCycle.builder()
                .apartment(apartment)
                .periodMonth(periodMonth)
                .status(BillingCycleStatus.OPEN)
                .openedAt(Instant.now())
                .build();

        return toResponse(billingCycleRepository.save(cycle));
    }

    public List<BillingCycleResponse> list(Apartment apartment) {
        return billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment).stream()
                .map(this::toResponse)
                .toList();
    }

    public BillingCycleDetailResponse getDetail(Apartment apartment, Long id) {
        BillingCycle cycle = requireOwnedCycle(apartment, id);
        List<InvoiceResponse> invoices = invoiceRepository.findByBillingCycle(cycle).stream()
                .map(this::toInvoiceResponse)
                .toList();
        return new BillingCycleDetailResponse(toResponse(cycle), invoices);
    }

    @Transactional
    public BillingCycleDetailResponse finalizeCycle(Apartment apartment, Long id) {
        BillingCycle cycle = requireOwnedCycle(apartment, id);
        if (cycle.getStatus() != BillingCycleStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an open billing cycle can be finalized");
        }

        TariffPlanResponse plan = tariffPlanService.getOrDefault(apartment);
        CommonAreaConfigResponse commonAreaConfig = commonAreaConfigService.getOrDefault(apartment);
        String monthPrefix = cycle.getPeriodMonth();

        record Billable(Household household, BigDecimal consumption) {
        }

        List<Billable> billableHouseholds = new ArrayList<>();
        for (Household household : householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)) {
            if (residentRepository.findByHousehold(household).isEmpty()) {
                // A household row with no resident is stale/orphaned data (e.g. left behind when a
                // resident's house number was edited to a value that resolved to a different Household
                // row) rather than a real occupied unit — skip it so it doesn't appear as a billed
                // household with a bogus zero-amount invoice.
                continue;
            }

            BigDecimal consumption = waterUsageReadingService.listForHousehold(household).stream()
                    .filter(reading -> monthPrefix.equals(reading.consumptionMonth()))
                    .map(WaterUsageReadingResponse::consumption)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            billableHouseholds.add(new Billable(household, consumption));
        }

        // Common-area apportionment needs every billable household's consumption/floor-area totals
        // up front, so it's computed once here rather than per-household inside the invoice loop below.
        Map<Long, BigDecimal> consumptionByHouseholdId = new LinkedHashMap<>();
        Map<Long, BigDecimal> sqftByHouseholdId = new LinkedHashMap<>();
        for (Billable billable : billableHouseholds) {
            consumptionByHouseholdId.put(billable.household().getId(), billable.consumption());
            BigDecimal sqft = billable.household().getFlatSizeSqft();
            sqftByHouseholdId.put(billable.household().getId(), sqft != null ? sqft : BigDecimal.ZERO);
        }
        Map<Long, List<CommonAreaChargeLine>> commonAreaChargesByHouseholdId =
                commonAreaConfigService.apportion(commonAreaConfig, consumptionByHouseholdId, sqftByHouseholdId);

        for (Billable billable : billableHouseholds) {
            Household household = billable.household();
            BigDecimal consumption = billable.consumption();

            List<TierChargeLine> breakdown = tariffPlanService.calculateBreakdown(consumption, plan.tiers());
            BigDecimal tariffTotal = breakdown.stream().map(TierChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<CommonAreaChargeLine> commonAreaLines = commonAreaChargesByHouseholdId.getOrDefault(household.getId(), List.of());
            BigDecimal commonAreaTotal = commonAreaLines.stream().map(CommonAreaChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            Invoice invoice = Invoice.builder()
                    .billingCycle(cycle)
                    .household(household)
                    .consumptionUnits(consumption)
                    .tariffAmount(tariffTotal)
                    .commonAreaAmount(commonAreaTotal)
                    .totalAmount(tariffTotal.add(commonAreaTotal))
                    .generatedAt(Instant.now())
                    .build();

            for (TierChargeLine line : breakdown) {
                invoice.getLineItems().add(InvoiceLineItem.builder()
                        .invoice(invoice)
                        .tierOrder(line.tierOrder())
                        .unitsInTier(line.unitsInTier())
                        .ratePerUnit(line.ratePerUnit())
                        .amount(line.amount())
                        .build());
            }

            for (CommonAreaChargeLine line : commonAreaLines) {
                invoice.getCommonAreaCharges().add(InvoiceCommonAreaCharge.builder()
                        .invoice(invoice)
                        .costItemId(line.costItemId())
                        .label(line.label())
                        .amount(line.amount())
                        .distributionMethod(commonAreaConfig.distributionMethod())
                        .build());
            }

            cycle.getInvoices().add(invoice);
        }

        cycle.setStatus(BillingCycleStatus.FINALIZED);
        cycle.setFinalizedAt(Instant.now());

        return getDetail(apartment, billingCycleRepository.save(cycle).getId());
    }

    @Transactional
    public BillingCycleResponse archive(Apartment apartment, Long id) {
        BillingCycle cycle = requireOwnedCycle(apartment, id);
        if (cycle.getStatus() != BillingCycleStatus.FINALIZED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a finalized billing cycle can be archived");
        }

        cycle.setStatus(BillingCycleStatus.ARCHIVED);
        cycle.setArchivedAt(Instant.now());

        return toResponse(billingCycleRepository.save(cycle));
    }

    @Transactional
    public InvoiceResponse setInvoicePaid(Apartment apartment, Long invoiceId, boolean paid) {
        Invoice invoice = invoiceRepository.findByIdAndBillingCycle_Apartment(invoiceId, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));

        invoice.setPaid(paid);
        invoice.setPaidAt(paid ? Instant.now() : null);

        return toInvoiceResponse(invoiceRepository.save(invoice));
    }

    public List<InvoiceResponse> listInvoicesForResident(Resident resident) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            return List.of();
        }

        // Same lazy-loading precaution as TariffPlanService.estimateForResident: resident.getHousehold()
        // may be a detached lazy proxy loaded during JWT authentication.
        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        return invoiceRepository.findByHouseholdOrderByGeneratedAtDesc(household).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    private BillingCycle requireOwnedCycle(Apartment apartment, Long id) {
        return billingCycleRepository.findByIdAndApartment(id, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Billing cycle not found"));
    }

    private BillingCycleResponse toResponse(BillingCycle cycle) {
        BigDecimal totalBilled = cycle.getInvoices().stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BillingCycleResponse(
                cycle.getId(),
                cycle.getPeriodMonth(),
                cycle.getStatus(),
                cycle.getOpenedAt(),
                cycle.getFinalizedAt(),
                cycle.getArchivedAt(),
                cycle.getInvoices().size(),
                totalBilled
        );
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        List<TierChargeLine> lineItems = invoice.getLineItems().stream()
                .map(item -> new TierChargeLine(item.getTierOrder(), item.getUnitsInTier(), item.getRatePerUnit(), item.getAmount()))
                .toList();

        List<CommonAreaChargeLine> commonAreaCharges = invoice.getCommonAreaCharges().stream()
                .map(charge -> new CommonAreaChargeLine(charge.getCostItemId(), charge.getLabel(), charge.getAmount()))
                .toList();

        Household household = invoice.getHousehold();
        String residentName = residentRepository.findByHousehold(household)
                .map(Resident::getFullName)
                .orElse(null);
        String meterNumber = waterMeterRepository.findByHousehold(household)
                .map(WaterMeter::getMeterNumber)
                .orElse(null);

        return new InvoiceResponse(
                invoice.getId(),
                household.getId(),
                household.getHouseNumber(),
                residentName,
                meterNumber,
                invoice.getBillingCycle().getPeriodMonth(),
                invoice.getConsumptionUnits(),
                invoice.getTotalAmount(),
                invoice.getTariffAmount(),
                invoice.getCommonAreaAmount(),
                invoice.getGeneratedAt(),
                invoice.isPaid(),
                invoice.getPaidAt(),
                lineItems,
                commonAreaCharges
        );
    }
}

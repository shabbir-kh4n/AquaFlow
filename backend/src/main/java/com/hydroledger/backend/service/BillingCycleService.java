package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.BillingCycleDetailResponse;
import com.hydroledger.backend.dto.BillingCycleResponse;
import com.hydroledger.backend.dto.CommonAreaChargeLine;
import com.hydroledger.backend.dto.CommonAreaConfigResponse;
import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.dto.TariffPlanResponse;
import com.hydroledger.backend.dto.TierChargeLine;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.BillingCycleStatus;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.InvoiceCommonAreaCharge;
import com.hydroledger.backend.entity.InvoiceLineItem;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;

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
    private final EmailService emailService;
    private final InvoicePdfService invoicePdfService;
    private final ResidentRecommendationService residentRecommendationService;
    private final PushNotificationService pushNotificationService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

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

        record Billable(Household household, BigDecimal consumption, BigDecimal previousReadingValue, BigDecimal currentReadingValue) {
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

            List<WaterUsageReadingResponse> monthReadings = waterUsageReadingService.listForHousehold(household).stream()
                    .filter(reading -> monthPrefix.equals(reading.consumptionMonth()))
                    .toList();

            BigDecimal consumption = monthReadings.stream()
                    .map(WaterUsageReadingResponse::consumption)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // The invoice PDF shows "previous reading -> current reading" like a physical utility
            // bill: the current reading is the latest meter value recorded this month, and since
            // consumption is a telescoping sum of deltas, the previous reading is simply that value
            // minus the month's total consumption - no separate baseline lookup needed.
            BigDecimal currentReadingValue = monthReadings.stream()
                    .max(java.util.Comparator.comparing(WaterUsageReadingResponse::readingDate))
                    .map(WaterUsageReadingResponse::readingValue)
                    .orElse(null);
            BigDecimal previousReadingValue = currentReadingValue != null ? currentReadingValue.subtract(consumption) : null;

            billableHouseholds.add(new Billable(household, consumption, previousReadingValue, currentReadingValue));
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

            BigDecimal baseAmount = tariffPlanService.calculateBaseAmount(consumption, plan.basePricePerUnit());
            List<TierChargeLine> breakdown = tariffPlanService.calculateBreakdown(consumption, plan.tiers());
            BigDecimal tariffTotal = breakdown.stream().map(TierChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<CommonAreaChargeLine> commonAreaLines = commonAreaChargesByHouseholdId.getOrDefault(household.getId(), List.of());
            BigDecimal commonAreaTotal = commonAreaLines.stream().map(CommonAreaChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            Invoice invoice = Invoice.builder()
                    .billingCycle(cycle)
                    .household(household)
                    .consumptionUnits(consumption)
                    .previousReadingValue(billable.previousReadingValue())
                    .currentReadingValue(billable.currentReadingValue())
                    .basePricePerUnit(plan.basePricePerUnit())
                    .baseAmount(baseAmount)
                    .tariffAmount(tariffTotal)
                    .commonAreaAmount(commonAreaTotal)
                    .totalAmount(baseAmount.add(tariffTotal).add(commonAreaTotal))
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

            residentRepository.findByHousehold(household).ifPresent(resident -> {
                String link = frontendUrl + "/resident/billing";
                byte[] invoicePdf = invoicePdfService.generate(toInvoiceResponse(invoice), apartment.getName());
                emailService.sendInvoiceReadyEmail(resident, cycle.getPeriodMonth(), invoice.getTotalAmount(), link, invoicePdf);
                pushNotificationService.sendInvoiceReadyPush(resident, cycle.getPeriodMonth(), invoice.getTotalAmount(), link);
                // Fire-and-forget: recommendation generation must never block finalize (a live
                // per-household Gemini call here previously stalled the whole request).
                residentRecommendationService.generateForResidentAsync(resident, cycle.getPeriodMonth());
            });
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

    @Transactional
    public InvoiceResponse payInvoiceAsResident(Resident resident, Long invoiceId) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
        }

        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        Invoice invoice = invoiceRepository.findByIdAndHousehold(invoiceId, household)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.isPaid()) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice is already paid");
        }

        invoice.setPaid(true);
        invoice.setPaidAt(Instant.now());

        return toInvoiceResponse(invoiceRepository.save(invoice));
    }

    public byte[] generateInvoicePdfForAdmin(Apartment apartment, Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndBillingCycle_Apartment(invoiceId, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return invoicePdfService.generate(toInvoiceResponse(invoice), apartment.getName());
    }

    public byte[] generateInvoicePdfForResident(Resident resident, Long invoiceId) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
        }

        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        Invoice invoice = invoiceRepository.findByIdAndHousehold(invoiceId, household)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));

        return invoicePdfService.generate(toInvoiceResponse(invoice), household.getApartment().getName());
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
        Optional<WaterMeter> meter = waterMeterRepository.findByHousehold(household);
        String meterNumber = meter.map(WaterMeter::getMeterNumber).orElse(null);
        String meterStatus = meter.map(m -> m.getStatus().name()).orElse(null);

        return new InvoiceResponse(
                invoice.getId(),
                household.getId(),
                household.getHouseNumber(),
                residentName,
                meterNumber,
                meterStatus,
                household.getFlatSizeSqft(),
                invoice.getBillingCycle().getPeriodMonth(),
                invoice.getConsumptionUnits(),
                invoice.getPreviousReadingValue(),
                invoice.getCurrentReadingValue(),
                invoice.getBasePricePerUnit(),
                invoice.getBaseAmount(),
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

package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.AdminReportResponse;
import com.hydroledger.backend.dto.AdminReportResponse.AdminReportSummary;
import com.hydroledger.backend.dto.AdminReportResponse.HouseholdReportRow;
import com.hydroledger.backend.dto.AdminReportResponse.MonthlyTrendPoint;
import com.hydroledger.backend.dto.AdminReportResponse.ReportRangeInfo;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.BillingCycleStatus;
import com.hydroledger.backend.entity.BulkWaterPurchase;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.BulkWaterPurchaseRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;

import lombok.RequiredArgsConstructor;

/**
 * Community-admin counterpart of {@link PlatformReportService}: same range presets/validation and
 * the same consumptionMonth grouping, but scoped to a single apartment and broken down per household,
 * with collection (paid vs outstanding), common-area and bulk-purchase figures on top.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final Map<String, Integer> PRESET_MONTHS = Map.of("3mo", 3, "6mo", 6, "12mo", 12);
    private static final String DEFAULT_RANGE = "3mo";
    private static final int TOP_CONSUMER_COUNT = 5;
    private static final BigDecimal LITERS_PER_KL = new BigDecimal("1000");
    // Invoices only exist once a cycle is finalized; archived cycles keep theirs. OPEN cycles are
    // excluded explicitly so a report never counts a cycle that hasn't been billed yet.
    private static final Set<BillingCycleStatus> BILLED_STATUSES = EnumSet.of(BillingCycleStatus.FINALIZED, BillingCycleStatus.ARCHIVED);

    private final HouseholdRepository householdRepository;
    private final ResidentRepository residentRepository;
    private final WaterMeterRepository waterMeterRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final BulkWaterPurchaseRepository bulkWaterPurchaseRepository;
    private final WaterUsageReadingService waterUsageReadingService;

    @Transactional(readOnly = true)
    public AdminReportResponse getReport(Apartment apartment, String range, String from, String to) {
        List<YearMonth> months = resolveMonths(range, from, to);
        Set<String> monthKeys = new LinkedHashSet<>();
        Map<String, BigDecimal> consumptionByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> billedByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> paidByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> bulkCostByMonth = new LinkedHashMap<>();
        for (YearMonth month : months) {
            String key = month.toString();
            monthKeys.add(key);
            consumptionByMonth.put(key, BigDecimal.ZERO);
            billedByMonth.put(key, BigDecimal.ZERO);
            paidByMonth.put(key, BigDecimal.ZERO);
            bulkCostByMonth.put(key, BigDecimal.ZERO);
        }

        // Invoices in range, grouped by household id.
        Map<Long, List<Invoice>> invoicesByHouseholdId = new HashMap<>();
        BigDecimal commonAreaTotal = BigDecimal.ZERO;
        for (BillingCycle cycle : billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)) {
            if (!monthKeys.contains(cycle.getPeriodMonth()) || !BILLED_STATUSES.contains(cycle.getStatus())) {
                continue;
            }
            for (Invoice invoice : invoiceRepository.findByBillingCycle(cycle)) {
                invoicesByHouseholdId.computeIfAbsent(invoice.getHousehold().getId(), id -> new ArrayList<>()).add(invoice);
                billedByMonth.merge(cycle.getPeriodMonth(), invoice.getTotalAmount(), BigDecimal::add);
                if (invoice.isPaid()) {
                    paidByMonth.merge(cycle.getPeriodMonth(), invoice.getTotalAmount(), BigDecimal::add);
                }
                commonAreaTotal = commonAreaTotal.add(nullToZero(invoice.getCommonAreaAmount()));
            }
        }

        List<HouseholdReportRow> rows = new ArrayList<>();
        BigDecimal totalConsumption = BigDecimal.ZERO;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int invoiceCount = 0;
        int unpaidInvoiceCount = 0;

        for (Household household : householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)) {
            BigDecimal consumption = BigDecimal.ZERO;
            for (WaterUsageReadingResponse reading : waterUsageReadingService.listForHousehold(household)) {
                if (monthKeys.contains(reading.consumptionMonth()) && reading.consumption() != null) {
                    consumption = consumption.add(reading.consumption());
                    consumptionByMonth.merge(reading.consumptionMonth(), reading.consumption(), BigDecimal::add);
                }
            }

            List<Invoice> invoices = invoicesByHouseholdId.getOrDefault(household.getId(), List.of());
            Optional<Resident> resident = residentRepository.findByHousehold(household);
            // Same stale/orphaned-household rule as BillingCycleService.finalizeCycle: a household row
            // with no resident and nothing to report in the range isn't a real occupied unit.
            if (resident.isEmpty() && invoices.isEmpty() && consumption.signum() == 0) {
                continue;
            }

            BigDecimal billed = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            int unpaid = 0;
            for (Invoice invoice : invoices) {
                billed = billed.add(invoice.getTotalAmount());
                if (invoice.isPaid()) {
                    paid = paid.add(invoice.getTotalAmount());
                } else {
                    unpaid++;
                }
            }

            rows.add(new HouseholdReportRow(
                    household.getId(),
                    household.getHouseNumber(),
                    resident.map(Resident::getFullName).orElse(null),
                    waterMeterRepository.findByHousehold(household).map(WaterMeter::getMeterNumber).orElse(null),
                    consumption,
                    perMonth(consumption, months.size()),
                    billed,
                    paid,
                    billed.subtract(paid),
                    invoices.size(),
                    unpaid
            ));

            totalConsumption = totalConsumption.add(consumption);
            totalBilled = totalBilled.add(billed);
            totalPaid = totalPaid.add(paid);
            invoiceCount += invoices.size();
            unpaidInvoiceCount += unpaid;
        }

        BigDecimal bulkCost = BigDecimal.ZERO;
        BigDecimal bulkLiters = BigDecimal.ZERO;
        int bulkCount = 0;
        for (BulkWaterPurchase purchase : bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)) {
            String key = YearMonth.from(purchase.getPurchaseDate()).toString();
            if (!monthKeys.contains(key)) {
                continue;
            }
            bulkCost = bulkCost.add(purchase.getTotalCost());
            bulkLiters = bulkLiters.add(purchase.getVolumeLiters());
            bulkCostByMonth.merge(key, purchase.getTotalCost(), BigDecimal::add);
            bulkCount++;
        }

        List<HouseholdReportRow> topConsumers = rows.stream()
                .filter(row -> row.totalConsumptionKl().signum() > 0)
                .sorted(Comparator.comparing(HouseholdReportRow::totalConsumptionKl).reversed())
                .limit(TOP_CONSUMER_COUNT)
                .toList();

        List<MonthlyTrendPoint> monthlyTrend = monthKeys.stream()
                .map(key -> new MonthlyTrendPoint(key, consumptionByMonth.get(key), billedByMonth.get(key),
                        paidByMonth.get(key), bulkCostByMonth.get(key)))
                .toList();

        AdminReportSummary summary = new AdminReportSummary(
                rows.size(),
                totalConsumption,
                perMonth(totalConsumption, months.size()),
                totalBilled,
                totalPaid,
                totalBilled.subtract(totalPaid),
                collectionRate(totalPaid, totalBilled),
                invoiceCount,
                unpaidInvoiceCount,
                commonAreaTotal,
                bulkCount,
                bulkCost,
                bulkLiters.divide(LITERS_PER_KL, 3, RoundingMode.HALF_UP),
                totalBilled.subtract(bulkCost)
        );

        String rangeKey;
        if (from != null || to != null) {
            rangeKey = "custom";
        } else if (range != null && PRESET_MONTHS.containsKey(range)) {
            rangeKey = range;
        } else {
            rangeKey = DEFAULT_RANGE;
        }
        ReportRangeInfo rangeInfo = new ReportRangeInfo(
                rangeKey,
                months.get(0).toString(),
                months.get(months.size() - 1).toString(),
                months.stream().map(YearMonth::toString).toList());

        return new AdminReportResponse(apartment.getName(), rangeInfo, summary, rows, topConsumers, monthlyTrend);
    }

    private BigDecimal collectionRate(BigDecimal paid, BigDecimal billed) {
        if (billed.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return paid.multiply(new BigDecimal("100")).divide(billed, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal perMonth(BigDecimal total, int monthCount) {
        if (monthCount == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(monthCount), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private List<YearMonth> resolveMonths(String range, String from, String to) {
        if (from != null || to != null) {
            if (from == null || to == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Both 'from' and 'to' are required for a custom range");
            }
            YearMonth fromMonth = parseMonth(from);
            YearMonth toMonth = parseMonth(to);
            if (fromMonth.isAfter(toMonth)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
            }

            List<YearMonth> months = new ArrayList<>();
            YearMonth cursor = fromMonth;
            while (!cursor.isAfter(toMonth)) {
                months.add(cursor);
                cursor = cursor.plusMonths(1);
            }
            return months;
        }

        int monthCount = range != null && PRESET_MONTHS.containsKey(range) ? PRESET_MONTHS.get(range) : PRESET_MONTHS.get(DEFAULT_RANGE);
        YearMonth current = YearMonth.now();
        List<YearMonth> months = new ArrayList<>();
        for (int i = monthCount - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid month format (expected YYYY-MM): " + value);
        }
    }
}

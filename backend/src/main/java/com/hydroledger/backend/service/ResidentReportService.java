package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.dto.ResidentReportResponse;
import com.hydroledger.backend.dto.ResidentReportResponse.MonthRow;
import com.hydroledger.backend.dto.ResidentReportResponse.ReportRange;
import com.hydroledger.backend.dto.ResidentReportResponse.Summary;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resident-facing personal usage & spending report and the account-statement PDF. Everything is
 * scoped to the caller's own household: invoices come from BillingCycleService.listInvoicesForResident
 * and readings from WaterUsageReadingService.listForHousehold, grouped by consumptionMonth the same
 * way the usage-comparison endpoint does.
 */
@Service
@RequiredArgsConstructor
public class ResidentReportService {

    private static final Map<String, Integer> PRESET_MONTHS = Map.of("3mo", 3, "6mo", 6, "12mo", 12);
    private static final String DEFAULT_RANGE = "6mo";

    private final HouseholdRepository householdRepository;
    private final BillingCycleService billingCycleService;
    private final WaterUsageReadingService waterUsageReadingService;
    private final StatementPdfService statementPdfService;

    public byte[] generateStatementPdf(Resident resident) {
        Household household = requireHousehold(resident);
        List<InvoiceResponse> invoices = new ArrayList<>(billingCycleService.listInvoicesForResident(resident));
        invoices.sort(Comparator.comparing(InvoiceResponse::periodMonth, Comparator.nullsLast(Comparator.naturalOrder())));
        return statementPdfService.generate(household.getApartment().getName(), household.getHouseNumber(),
                resident.getFullName(), invoices);
    }

    public ResidentReportResponse getReport(Resident resident, String range, String from, String to) {
        List<YearMonth> months = resolveMonths(range, from, to);
        YearMonth first = months.get(0);
        YearMonth last = months.get(months.size() - 1);
        YearMonth previousFrom = first.minusMonths(months.size());
        YearMonth previousTo = first.minusMonths(1);

        Household household = requireHousehold(resident);

        Map<String, BigDecimal> consumptionByMonth = new HashMap<>();
        for (WaterUsageReadingResponse reading : waterUsageReadingService.listForHousehold(household)) {
            if (reading.consumptionMonth() != null && reading.consumption() != null) {
                consumptionByMonth.merge(reading.consumptionMonth(), reading.consumption(), BigDecimal::add);
            }
        }

        Map<String, List<InvoiceResponse>> invoicesByMonth = new HashMap<>();
        for (InvoiceResponse invoice : billingCycleService.listInvoicesForResident(resident)) {
            invoicesByMonth.computeIfAbsent(invoice.periodMonth(), k -> new ArrayList<>()).add(invoice);
        }

        List<MonthRow> rows = new ArrayList<>();
        BigDecimal totalConsumption = BigDecimal.ZERO;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int monthsWithConsumption = 0;

        for (YearMonth month : months) {
            String key = month.toString();
            BigDecimal consumption = consumptionByMonth.getOrDefault(key, BigDecimal.ZERO);
            if (consumptionByMonth.containsKey(key)) {
                monthsWithConsumption++;
            }

            List<InvoiceResponse> monthInvoices = invoicesByMonth.getOrDefault(key, List.of());
            BigDecimal billed = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            for (InvoiceResponse invoice : monthInvoices) {
                BigDecimal amount = invoice.totalAmount() != null ? invoice.totalAmount() : BigDecimal.ZERO;
                billed = billed.add(amount);
                if (invoice.paid()) {
                    paid = paid.add(amount);
                }
            }
            boolean invoiced = !monthInvoices.isEmpty();
            boolean allPaid = invoiced && monthInvoices.stream().allMatch(InvoiceResponse::paid);

            rows.add(new MonthRow(key, consumption, billed, paid, invoiced, allPaid));
            totalConsumption = totalConsumption.add(consumption);
            totalBilled = totalBilled.add(billed);
            totalPaid = totalPaid.add(paid);
        }

        BigDecimal previousConsumption = BigDecimal.ZERO;
        for (YearMonth month = previousFrom; !month.isAfter(previousTo); month = month.plusMonths(1)) {
            previousConsumption = previousConsumption.add(consumptionByMonth.getOrDefault(month.toString(), BigDecimal.ZERO));
        }

        BigDecimal avgMonthly = monthsWithConsumption == 0
                ? BigDecimal.ZERO
                : totalConsumption.divide(BigDecimal.valueOf(monthsWithConsumption), 3, RoundingMode.HALF_UP);
        BigDecimal changePercent = previousConsumption.signum() == 0
                ? null
                : totalConsumption.subtract(previousConsumption)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousConsumption, 1, RoundingMode.HALF_UP);

        Summary summary = new Summary(totalConsumption, totalBilled, totalPaid, totalBilled.subtract(totalPaid),
                avgMonthly, previousConsumption, changePercent);

        String rangeKey;
        if (from != null || to != null) {
            rangeKey = "custom";
        } else if (range != null && PRESET_MONTHS.containsKey(range)) {
            rangeKey = range;
        } else {
            rangeKey = DEFAULT_RANGE;
        }
        ReportRange rangeInfo = new ReportRange(rangeKey, first.toString(), last.toString(),
                previousFrom.toString(), previousTo.toString());

        return new ResidentReportResponse(rangeInfo, summary, rows);
    }

    private Household requireHousehold(Resident resident) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Household not found");
        }
        // resident.getHousehold() may be a detached lazy proxy loaded during JWT authentication.
        return householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));
    }

    // Same validation rules/messages as PlatformReportService.resolveMonths.
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
            if (fromMonth.plusMonths(120).isBefore(toMonth)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A custom range can span at most 10 years");
            }

            List<YearMonth> months = new ArrayList<>();
            for (YearMonth cursor = fromMonth; !cursor.isAfter(toMonth); cursor = cursor.plusMonths(1)) {
                months.add(cursor);
            }
            return months;
        }

        int monthCount = PRESET_MONTHS.getOrDefault(range == null ? DEFAULT_RANGE : range, PRESET_MONTHS.get(DEFAULT_RANGE));
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

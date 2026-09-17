package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.dto.PlatformReportResponse;
import com.hydroledger.backend.dto.PlatformReportResponse.CommunityReportRow;
import com.hydroledger.backend.dto.PlatformReportResponse.MonthlyTrendPoint;
import com.hydroledger.backend.dto.PlatformReportResponse.PlatformReportSummary;
import com.hydroledger.backend.dto.PlatformReportResponse.ReportRangeInfo;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformReportService {

    private static final Map<String, Integer> PRESET_MONTHS = Map.of("3mo", 3, "6mo", 6, "12mo", 12);
    private static final String DEFAULT_RANGE = "3mo";

    private final CommunityAdminRepository communityAdminRepository;
    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final ResidentRepository residentRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final WaterUsageReadingService waterUsageReadingService;

    public PlatformReportResponse getReport(String range, String from, String to) {
        List<YearMonth> months = resolveMonths(range, from, to);
        Set<String> monthKeys = new LinkedHashSet<>();
        Map<String, BigDecimal> consumptionByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> billedByMonth = new LinkedHashMap<>();
        for (YearMonth month : months) {
            String key = month.toString();
            monthKeys.add(key);
            consumptionByMonth.put(key, BigDecimal.ZERO);
            billedByMonth.put(key, BigDecimal.ZERO);
        }

        List<CommunityReportRow> rows = new ArrayList<>();
        int totalResidents = 0;
        BigDecimal totalConsumption = BigDecimal.ZERO;
        BigDecimal totalBilled = BigDecimal.ZERO;

        for (CommunityAdmin admin : communityAdminRepository.findAll()) {
            Optional<Apartment> apartmentOpt = apartmentRepository.findByAdmin(admin);
            if (apartmentOpt.isEmpty()) {
                continue;
            }
            Apartment apartment = apartmentOpt.get();

            int residentCount = residentRepository.findByCreatedByOrderByCreatedAtDesc(admin).size();

            BigDecimal communityConsumption = BigDecimal.ZERO;
            for (Household household : householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)) {
                for (WaterUsageReadingResponse reading : waterUsageReadingService.listForHousehold(household)) {
                    if (monthKeys.contains(reading.consumptionMonth())) {
                        communityConsumption = communityConsumption.add(reading.consumption());
                        consumptionByMonth.merge(reading.consumptionMonth(), reading.consumption(), BigDecimal::add);
                    }
                }
            }

            BigDecimal communityBilled = BigDecimal.ZERO;
            for (BillingCycle cycle : billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)) {
                if (!monthKeys.contains(cycle.getPeriodMonth())) {
                    continue;
                }
                for (Invoice invoice : invoiceRepository.findByBillingCycle(cycle)) {
                    communityBilled = communityBilled.add(invoice.getTotalAmount());
                    billedByMonth.merge(cycle.getPeriodMonth(), invoice.getTotalAmount(), BigDecimal::add);
                }
            }

            rows.add(new CommunityReportRow(
                    admin.getId(),
                    apartment.getId(),
                    apartment.getName(),
                    admin.getFullName(),
                    residentCount,
                    communityConsumption,
                    communityBilled,
                    perResident(communityConsumption, residentCount),
                    perResident(communityBilled, residentCount)
            ));

            totalResidents += residentCount;
            totalConsumption = totalConsumption.add(communityConsumption);
            totalBilled = totalBilled.add(communityBilled);
        }

        List<MonthlyTrendPoint> monthlyTrend = monthKeys.stream()
                .map(key -> new MonthlyTrendPoint(key, consumptionByMonth.get(key), billedByMonth.get(key)))
                .toList();

        PlatformReportSummary summary = new PlatformReportSummary(
                rows.size(),
                totalResidents,
                totalConsumption,
                totalBilled,
                perResident(totalConsumption, totalResidents),
                perResident(totalBilled, totalResidents)
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

        return new PlatformReportResponse(rangeInfo, summary, rows, monthlyTrend);
    }

    private BigDecimal perResident(BigDecimal total, int residentCount) {
        if (residentCount == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(residentCount), 3, RoundingMode.HALF_UP);
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

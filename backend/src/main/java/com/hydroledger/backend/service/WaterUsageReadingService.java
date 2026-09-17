package com.hydroledger.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.hydroledger.backend.dto.ConsumptionComparisonPoint;
import com.hydroledger.backend.dto.CsvRowFailure;
import com.hydroledger.backend.dto.CsvUploadResultResponse;
import com.hydroledger.backend.dto.WaterUsageReadingRequest;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.entity.WaterUsageReading;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.repository.WaterUsageReadingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaterUsageReadingService {

    private static final BigDecimal SIMILAR_SIZE_BAND = new BigDecimal("0.20");

    private final WaterUsageReadingRepository waterUsageReadingRepository;
    private final WaterMeterRepository waterMeterRepository;
    private final HouseholdRepository householdRepository;
    private final ApartmentService apartmentService;

    @Transactional
    public WaterUsageReadingResponse recordManual(WaterUsageReadingRequest request, CommunityAdmin admin) {
        Apartment apartment = apartmentService.getOrCreate(admin);
        WaterMeter meter = waterMeterRepository.findByApartmentAndMeterNumber(apartment, request.meterNumber())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Meter not found"));

        if (waterUsageReadingRepository.existsByMeterAndReadingDate(meter, request.readingDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "A reading already exists for this meter on this date");
        }

        Optional<WaterUsageReading> previous = waterUsageReadingRepository
                .findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, request.readingDate());

        if (previous.isPresent() && request.readingValue().compareTo(previous.get().getReadingValue()) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "New reading (" + request.readingValue() + " kL) cannot be lower than the previous recorded reading ("
                            + previous.get().getReadingValue() + " kL) on " + previous.get().getReadingDate());
        }

        WaterUsageReading reading = WaterUsageReading.builder()
                .meter(meter)
                .readingDate(request.readingDate())
                .readingValue(request.readingValue())
                .source(ReadingSource.MANUAL)
                .recordedBy(admin)
                .build();

        waterUsageReadingRepository.save(reading);
        return WaterUsageReadingResponse.from(reading, computeConsumption(reading, previous),
                previous.map(WaterUsageReading::getReadingDate).orElse(null));
    }

    public List<WaterUsageReadingResponse> search(String meterNumber, LocalDate from, LocalDate to, CommunityAdmin admin) {
        Apartment apartment = apartmentService.getOrCreate(admin);
        return waterUsageReadingRepository.search(apartment, meterNumber, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<WaterUsageReadingResponse> listForResident(Resident resident) {
        Household household = resident.getHousehold();
        if (household == null) {
            return List.of();
        }

        return listForHousehold(household);
    }

    public List<WaterUsageReadingResponse> listForHousehold(Household household) {
        return waterMeterRepository.findByHousehold(household)
                .map(meter -> waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter).stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    public List<ConsumptionComparisonPoint> getComparisonForResident(Resident resident) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            return List.of();
        }

        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));
        Apartment apartment = household.getApartment();

        List<Household> households = householdRepository.findByApartmentOrderByHouseNumberAsc(apartment);

        // month -> householdId -> that household's total consumption for the month
        Map<String, Map<Long, BigDecimal>> byMonth = new TreeMap<>();
        for (Household h : households) {
            for (WaterUsageReadingResponse reading : listForHousehold(h)) {
                byMonth.computeIfAbsent(reading.consumptionMonth(), k -> new LinkedHashMap<>())
                        .merge(h.getId(), reading.consumption(), BigDecimal::add);
            }
        }

        BigDecimal residentSqft = household.getFlatSizeSqft();
        Set<Long> similarSizedIds = residentSqft == null || residentSqft.signum() <= 0
                ? Set.of()
                : households.stream()
                        .filter(h -> h.getFlatSizeSqft() != null && isWithinBand(h.getFlatSizeSqft(), residentSqft))
                        .map(Household::getId)
                        .collect(Collectors.toSet());

        List<ConsumptionComparisonPoint> points = new ArrayList<>();
        for (Map.Entry<String, Map<Long, BigDecimal>> entry : byMonth.entrySet()) {
            Map<Long, BigDecimal> perHousehold = entry.getValue();
            BigDecimal householdValue = perHousehold.getOrDefault(household.getId(), BigDecimal.ZERO);
            BigDecimal apartmentAverage = average(perHousehold.values());
            BigDecimal similarSizedAverage = similarSizedIds.isEmpty()
                    ? apartmentAverage
                    : average(perHousehold.entrySet().stream()
                            .filter(e -> similarSizedIds.contains(e.getKey()))
                            .map(Map.Entry::getValue)
                            .toList());
            points.add(new ConsumptionComparisonPoint(entry.getKey(), householdValue, apartmentAverage, similarSizedAverage));
        }
        return points;
    }

    private boolean isWithinBand(BigDecimal candidateSqft, BigDecimal referenceSqft) {
        BigDecimal lower = referenceSqft.multiply(BigDecimal.ONE.subtract(SIMILAR_SIZE_BAND));
        BigDecimal upper = referenceSqft.multiply(BigDecimal.ONE.add(SIMILAR_SIZE_BAND));
        return candidateSqft.compareTo(lower) >= 0 && candidateSqft.compareTo(upper) <= 0;
    }

    private BigDecimal average(Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP);
    }

    @Transactional
    public CsvUploadResultResponse bulkUploadCsv(MultipartFile file, CommunityAdmin admin) {
        Apartment apartment = apartmentService.getOrCreate(admin);

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> collected = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                collected.add(line);
            }
            lines = collected;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read CSV file");
        }

        int totalRows = 0;
        int successCount = 0;
        List<CsvRowFailure> failures = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!StringUtils.hasText(line)) {
                continue;
            }

            totalRows++;
            int rowNumber = i + 1;

            try {
                processCsvRow(line, apartment, admin);
                successCount++;
            } catch (Exception ex) {
                failures.add(new CsvRowFailure(rowNumber, line, ex.getMessage()));
            }
        }

        return new CsvUploadResultResponse(totalRows, successCount, totalRows - successCount, failures);
    }

    private void processCsvRow(String line, Apartment apartment, CommunityAdmin admin) {
        String[] parts = line.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected 3 columns: meterNumber,readingDate,readingValue");
        }

        String meterNumber = parts[0].trim();
        LocalDate readingDate;
        BigDecimal readingValue;

        try {
            readingDate = LocalDate.parse(parts[1].trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid reading date: " + parts[1].trim());
        }

        try {
            readingValue = new BigDecimal(parts[2].trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid reading value: " + parts[2].trim());
        }

        WaterMeter meter = waterMeterRepository.findByApartmentAndMeterNumber(apartment, meterNumber)
                .orElseThrow(() -> new IllegalArgumentException("Unknown meter number: " + meterNumber));

        if (waterUsageReadingRepository.existsByMeterAndReadingDate(meter, readingDate)) {
            throw new IllegalArgumentException("A reading already exists for this meter and date");
        }

        Optional<WaterUsageReading> previous = waterUsageReadingRepository
                .findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, readingDate);

        if (previous.isPresent() && readingValue.compareTo(previous.get().getReadingValue()) < 0) {
            throw new IllegalArgumentException(
                    "New reading (" + readingValue + " kL) cannot be lower than the previous recorded reading ("
                            + previous.get().getReadingValue() + " kL) on " + previous.get().getReadingDate());
        }

        WaterUsageReading reading = WaterUsageReading.builder()
                .meter(meter)
                .readingDate(readingDate)
                .readingValue(readingValue)
                .source(ReadingSource.CSV)
                .recordedBy(admin)
                .build();

        waterUsageReadingRepository.save(reading);
    }

    public Optional<WaterUsageReading> getLatestForResident(Resident resident) {
        Household household = resident.getHousehold();
        if (household == null) {
            return Optional.empty();
        }

        return waterMeterRepository.findByHousehold(household)
                .flatMap(meter -> waterUsageReadingRepository.findByMeterOrderByReadingDateDesc(meter).stream()
                        .findFirst());
    }

    private WaterUsageReadingResponse toResponse(WaterUsageReading reading) {
        Optional<WaterUsageReading> previous = waterUsageReadingRepository
                .findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(reading.getMeter(), reading.getReadingDate());
        return WaterUsageReadingResponse.from(reading, computeConsumption(reading, previous),
                previous.map(WaterUsageReading::getReadingDate).orElse(null));
    }

    private BigDecimal computeConsumption(WaterUsageReading reading, Optional<WaterUsageReading> previous) {
        return previous.map(p -> reading.getReadingValue().subtract(p.getReadingValue()))
                .orElse(BigDecimal.ZERO);
    }
}

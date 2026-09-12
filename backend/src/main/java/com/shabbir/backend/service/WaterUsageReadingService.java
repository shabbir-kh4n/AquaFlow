package com.shabbir.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.shabbir.backend.dto.CsvRowFailure;
import com.shabbir.backend.dto.CsvUploadResultResponse;
import com.shabbir.backend.dto.WaterUsageReadingRequest;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.ReadingSource;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.entity.WaterUsageReading;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.WaterMeterRepository;
import com.shabbir.backend.repository.WaterUsageReadingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaterUsageReadingService {

    private final WaterUsageReadingRepository waterUsageReadingRepository;
    private final WaterMeterRepository waterMeterRepository;
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

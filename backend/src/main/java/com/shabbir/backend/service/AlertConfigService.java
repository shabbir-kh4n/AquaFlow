package com.shabbir.backend.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.AlertConfigRequest;
import com.shabbir.backend.dto.AlertConfigResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.AlertConfig;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.AlertConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertConfigService {

    private static final BigDecimal DEFAULT_STD_DEV_MULTIPLIER = new BigDecimal("2.0");

    private final AlertConfigRepository alertConfigRepository;

    public AlertConfigResponse getOrDefault(Apartment apartment) {
        return alertConfigRepository.findByApartment(apartment)
                .map(this::toResponse)
                .orElseGet(() -> new AlertConfigResponse(null, null, DEFAULT_STD_DEV_MULTIPLIER, true, true, false, null));
    }

    @Transactional
    public AlertConfigResponse save(Apartment apartment, AlertConfigRequest request) {
        if (request.thresholdAlertsEnabled()
                && (request.monthlyThresholdKl() == null || request.monthlyThresholdKl().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A positive monthly threshold (kL) is required when threshold alerts are enabled");
        }
        if (request.outlierStdDevMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Standard deviation multiplier must be positive");
        }

        AlertConfig config = alertConfigRepository.findByApartment(apartment)
                .orElseGet(() -> AlertConfig.builder().apartment(apartment).build());

        config.setMonthlyThresholdKl(request.monthlyThresholdKl());
        config.setOutlierStdDevMultiplier(request.outlierStdDevMultiplier());
        config.setThresholdAlertsEnabled(request.thresholdAlertsEnabled());
        config.setOutlierAlertsEnabled(request.outlierAlertsEnabled());

        return toResponse(alertConfigRepository.save(config));
    }

    private AlertConfigResponse toResponse(AlertConfig config) {
        return new AlertConfigResponse(
                config.getId(),
                config.getMonthlyThresholdKl(),
                config.getOutlierStdDevMultiplier(),
                config.isThresholdAlertsEnabled(),
                config.isOutlierAlertsEnabled(),
                true,
                config.getUpdatedAt());
    }
}

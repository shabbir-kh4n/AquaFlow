package com.hydroledger.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.hydroledger.backend.dto.AlertConfigRequest;
import com.hydroledger.backend.dto.AlertConfigResponse;
import com.hydroledger.backend.entity.AlertConfig;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AlertConfigRepository;
import com.hydroledger.backend.service.AlertConfigService;

@ExtendWith(MockitoExtension.class)
class AlertConfigServiceTests {

    @Mock
    private AlertConfigRepository alertConfigRepository;

    @InjectMocks
    private AlertConfigService alertConfigService;

    private Apartment apartment;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
    }

    @Test
    void getOrDefaultReturnsUnpersistedDefaultsWhenNoneSaved() {
        when(alertConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());

        AlertConfigResponse response = alertConfigService.getOrDefault(apartment);

        assertThat(response.persisted()).isFalse();
        assertThat(response.monthlyThresholdKl()).isNull();
        assertThat(response.outlierStdDevMultiplier()).isEqualByComparingTo("2.0");
        assertThat(response.thresholdAlertsEnabled()).isTrue();
        assertThat(response.outlierAlertsEnabled()).isTrue();
    }

    @Test
    void getOrDefaultReturnsPersistedConfig() {
        AlertConfig config = AlertConfig.builder().id(5L).apartment(apartment)
                .monthlyThresholdKl(new BigDecimal("20")).outlierStdDevMultiplier(new BigDecimal("2.5"))
                .thresholdAlertsEnabled(true).outlierAlertsEnabled(false).updatedAt(java.time.Instant.now()).build();
        when(alertConfigRepository.findByApartment(apartment)).thenReturn(Optional.of(config));

        AlertConfigResponse response = alertConfigService.getOrDefault(apartment);

        assertThat(response.persisted()).isTrue();
        assertThat(response.monthlyThresholdKl()).isEqualByComparingTo("20");
        assertThat(response.outlierAlertsEnabled()).isFalse();
    }

    @Test
    void saveRejectsMissingThresholdWhenThresholdAlertsEnabled() {
        AlertConfigRequest request = new AlertConfigRequest(null, new BigDecimal("2.0"), true, true);

        assertThatThrownBy(() -> alertConfigService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void saveRejectsNonPositiveStdDevMultiplier() {
        AlertConfigRequest request = new AlertConfigRequest(new BigDecimal("20"), BigDecimal.ZERO, true, true);

        assertThatThrownBy(() -> alertConfigService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void saveCreatesNewConfigWhenNonePersisted() {
        when(alertConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(alertConfigRepository.save(any(AlertConfig.class))).thenAnswer(inv -> {
            AlertConfig config = inv.getArgument(0);
            config.setId(1L);
            config.setUpdatedAt(java.time.Instant.now());
            return config;
        });

        AlertConfigRequest request = new AlertConfigRequest(new BigDecimal("25"), new BigDecimal("2.0"), true, true);

        AlertConfigResponse response = alertConfigService.save(apartment, request);

        assertThat(response.persisted()).isTrue();
        assertThat(response.monthlyThresholdKl()).isEqualByComparingTo("25");
    }
}

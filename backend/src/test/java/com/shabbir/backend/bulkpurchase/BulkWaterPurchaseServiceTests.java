package com.shabbir.backend.bulkpurchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.shabbir.backend.dto.BulkWaterPurchaseRequest;
import com.shabbir.backend.dto.BulkWaterPurchaseResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BulkWaterPurchase;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.BulkWaterPurchaseRepository;
import com.shabbir.backend.service.BulkWaterPurchaseService;

@ExtendWith(MockitoExtension.class)
class BulkWaterPurchaseServiceTests {

    @Mock
    private BulkWaterPurchaseRepository bulkWaterPurchaseRepository;

    @InjectMocks
    private BulkWaterPurchaseService bulkWaterPurchaseService;

    private Apartment apartment;
    private BulkWaterPurchaseRequest request;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
        request = new BulkWaterPurchaseRequest(LocalDate.of(2026, 9, 1), "ABC Tankers",
                new BigDecimal("5000"), new BigDecimal("0.85"), "Weekly delivery");
    }

    @Test
    void createComputesTotalCostFromVolumeAndUnitCost() {
        when(bulkWaterPurchaseRepository.save(any(BulkWaterPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BulkWaterPurchaseResponse response = bulkWaterPurchaseService.create(apartment, request);

        assertThat(response.totalCost()).isEqualByComparingTo("4250.00");
        assertThat(response.source()).isEqualTo("ABC Tankers");
    }

    @Test
    void listDelegatesToRepositoryOrderedByPurchaseDateDesc() {
        BulkWaterPurchase purchase = BulkWaterPurchase.builder().id(1L).apartment(apartment)
                .purchaseDate(LocalDate.of(2026, 9, 1)).source("ABC Tankers")
                .volumeLiters(new BigDecimal("5000")).unitCost(new BigDecimal("0.85"))
                .totalCost(new BigDecimal("4250.00")).build();
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of(purchase));

        List<BulkWaterPurchaseResponse> result = bulkWaterPurchaseService.list(apartment);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("ABC Tankers");
    }

    @Test
    void updateRecomputesTotalCost() {
        BulkWaterPurchase existing = BulkWaterPurchase.builder().id(1L).apartment(apartment)
                .purchaseDate(LocalDate.of(2026, 9, 1)).source("Old Supplier")
                .volumeLiters(new BigDecimal("1000")).unitCost(new BigDecimal("1.00"))
                .totalCost(new BigDecimal("1000.00")).build();
        when(bulkWaterPurchaseRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(existing));
        when(bulkWaterPurchaseRepository.save(any(BulkWaterPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BulkWaterPurchaseResponse response = bulkWaterPurchaseService.update(apartment, 1L, request);

        assertThat(response.totalCost()).isEqualByComparingTo("4250.00");
        assertThat(response.source()).isEqualTo("ABC Tankers");
    }

    @Test
    void updateThrowsNotFoundForPurchaseFromAnotherApartment() {
        when(bulkWaterPurchaseRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bulkWaterPurchaseService.update(apartment, 1L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteThrowsNotFoundForPurchaseFromAnotherApartment() {
        when(bulkWaterPurchaseRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bulkWaterPurchaseService.delete(apartment, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteRemovesOwnedPurchase() {
        BulkWaterPurchase existing = BulkWaterPurchase.builder().id(1L).apartment(apartment).build();
        when(bulkWaterPurchaseRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(existing));

        bulkWaterPurchaseService.delete(apartment, 1L);

        verify(bulkWaterPurchaseRepository).delete(existing);
    }
}

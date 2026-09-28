package com.hydroledger.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.BillingCycleStatus;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.service.BillingCycleService;
import com.hydroledger.backend.service.CommonAreaConfigService;
import com.hydroledger.backend.service.EmailService;
import com.hydroledger.backend.service.InvoicePdfService;
import com.hydroledger.backend.service.PushNotificationService;
import com.hydroledger.backend.service.ResidentRecommendationService;
import com.hydroledger.backend.service.TariffPlanService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class ResidentPayAllServiceTests {

    @Mock private BillingCycleRepository billingCycleRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private WaterMeterRepository waterMeterRepository;
    @Mock private WaterUsageReadingService waterUsageReadingService;
    @Mock private TariffPlanService tariffPlanService;
    @Mock private CommonAreaConfigService commonAreaConfigService;
    @Mock private EmailService emailService;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private ResidentRecommendationService residentRecommendationService;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks
    private BillingCycleService billingCycleService;

    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
    private final Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
    private final Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();

    private Invoice invoice(long id, String period, String amount, boolean paid) {
        BillingCycle cycle = BillingCycle.builder().id(id).apartment(apartment).periodMonth(period)
                .status(BillingCycleStatus.FINALIZED).build();
        Instant paidAt = paid ? Instant.parse("2026-01-01T00:00:00Z") : null;
        return Invoice.builder().id(id).billingCycle(cycle).household(household)
                .consumptionUnits(BigDecimal.TEN).totalAmount(new BigDecimal(amount))
                .generatedAt(Instant.now()).paid(paid).paidAt(paidAt).build();
    }

    @Test
    void payAllMarksOnlyTheUnpaidInvoicesOfTheOwnHouseholdPaid() {
        Invoice unpaid1 = invoice(1L, "2026-08", "100.00", false);
        Invoice unpaid2 = invoice(2L, "2026-07", "50.50", false);
        Invoice alreadyPaid = invoice(3L, "2026-06", "70.00", true);
        Instant originalPaidAt = alreadyPaid.getPaidAt();

        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(invoiceRepository.findByHouseholdOrderByGeneratedAtDesc(household)).thenReturn(List.of(unpaid1, unpaid2, alreadyPaid));
        when(invoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<InvoiceResponse> result = billingCycleService.payAllOutstandingAsResident(resident);

        assertThat(result).extracting(InvoiceResponse::id).containsExactly(1L, 2L);
        assertThat(result).allSatisfy(r -> {
            assertThat(r.paid()).isTrue();
            assertThat(r.paidAt()).isNotNull();
        });
        assertThat(unpaid1.isPaid()).isTrue();
        assertThat(unpaid2.isPaid()).isTrue();
        // The already-paid invoice is left alone (paidAt not overwritten).
        assertThat(alreadyPaid.getPaidAt()).isEqualTo(originalPaidAt);
        // Only invoices of the resident's own household are ever queried.
        verify(invoiceRepository).findByHouseholdOrderByGeneratedAtDesc(household);
    }

    @Test
    void payAllIsIdempotentWhenNothingIsUnpaid() {
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(invoiceRepository.findByHouseholdOrderByGeneratedAtDesc(household))
                .thenReturn(List.of(invoice(3L, "2026-06", "70.00", true)));

        assertThat(billingCycleService.payAllOutstandingAsResident(resident)).isEmpty();
        verify(invoiceRepository, never()).saveAll(anyList());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void payAllReturnsEmptyWhenResidentHasNoHousehold() {
        Resident noHousehold = Resident.builder().id(2L).fullName("Res").email("r2@example.com").household(null).build();

        assertThat(billingCycleService.payAllOutstandingAsResident(noHousehold)).isEmpty();
        verify(invoiceRepository, never()).saveAll(anyList());
    }
}

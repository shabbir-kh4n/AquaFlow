package com.hydroledger.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.hydroledger.backend.dto.AddTicketMessageRequest;
import com.hydroledger.backend.dto.CreateSupportTicketRequest;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SupportTicket;
import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.SupportTicketMessageRepository;
import com.hydroledger.backend.repository.SupportTicketRepository;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.EmailService;
import com.hydroledger.backend.service.SupportTicketService;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTests {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportTicketMessageRepository supportTicketMessageRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ApartmentService apartmentService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private SupportTicketService supportTicketService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(10L).name("Sunrise Towers").admin(admin).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(supportTicketService, "frontendUrl", "http://localhost:5175");
    }

    @Test
    void createFromResidentPersistsTicketAndFirstMessage() {
        Household household = Household.builder().id(5L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").household(household).build();

        when(householdRepository.findWithApartmentById(5L)).thenReturn(Optional.of(household));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return ticket;
        });

        SupportTicketResponse response = supportTicketService.createFromResident(
                resident, new CreateSupportTicketRequest("Leaking tap", "The kitchen tap won't stop dripping.", false, true));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.raisedByRole()).isEqualTo(SupportTicketRole.RESIDENT);
        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.toSystemAdmin()).isTrue();
        assertThat(response.toAdmin()).isFalse();
        verify(supportTicketMessageRepository).save(any());
    }

    @Test
    void createFromResidentThrowsWhenNoHousehold() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        assertThatThrownBy(() -> supportTicketService.createFromResident(
                resident, new CreateSupportTicketRequest("Leaking tap", "message", false, true)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createFromResidentThrowsWhenNoRecipientSelected() {
        Household household = Household.builder().id(5L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").household(household).build();

        when(householdRepository.findWithApartmentById(5L)).thenReturn(Optional.of(household));

        assertThatThrownBy(() -> supportTicketService.createFromResident(
                resident, new CreateSupportTicketRequest("Leaking tap", "message", false, false)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createFromResidentCanAddressBothRecipients() {
        Household household = Household.builder().id(5L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").household(household).build();

        when(householdRepository.findWithApartmentById(5L)).thenReturn(Optional.of(household));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicketResponse response = supportTicketService.createFromResident(
                resident, new CreateSupportTicketRequest("Leaking tap", "message", true, true));

        assertThat(response.toAdmin()).isTrue();
        assertThat(response.toSystemAdmin()).isTrue();
    }

    @Test
    void createFromAdminPersistsTicketAndFirstMessage() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(200L);
            return ticket;
        });

        SupportTicketResponse response = supportTicketService.createFromAdmin(
                admin, new CreateSupportTicketRequest("Billing question", "Why did the invoice change?", false, false));

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.raisedByRole()).isEqualTo(SupportTicketRole.ADMIN);
        // An admin's own ticket always targets System Admin only, regardless of the request's
        // recipient fields (those only matter for a resident-raised ticket).
        assertThat(response.toSystemAdmin()).isTrue();
        assertThat(response.toAdmin()).isFalse();
        verify(supportTicketMessageRepository).save(any());
    }

    @Test
    void listInboxForAdminReturnsResidentTicketsAddressedToAdmin() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident).subject("Leaking tap").toCommunityAdmin(true).toSystemAdmin(false).build();

        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(supportTicketRepository.findByApartmentAndRaisedByRoleAndToCommunityAdminTrueOrderByCreatedAtDesc(
                apartment, SupportTicketRole.RESIDENT)).thenReturn(java.util.List.of(ticket));

        var result = supportTicketService.listInboxForAdmin(admin);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).toAdmin()).isTrue();
    }

    @Test
    void addInboxMessageFromAdminAdvancesOpenTicketAndNotifiesResident() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident).subject("Leaking tap").toCommunityAdmin(true).status(SupportTicketStatus.OPEN).build();

        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(supportTicketRepository.findByIdAndApartmentAndToCommunityAdminTrue(1L, apartment)).thenReturn(Optional.of(ticket));
        when(supportTicketMessageRepository.findByTicketOrderByCreatedAtAsc(ticket)).thenReturn(java.util.List.of());

        SupportTicketDetailResponse response = supportTicketService.addInboxMessageFromAdmin(
                1L, admin, new AddTicketMessageRequest("Looking into this."));

        assertThat(response.status()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        verify(emailService).sendSupportTicketUpdateEmail(
                org.mockito.ArgumentMatchers.eq(resident.getEmail()), any(), any(), any(), any());
    }

    @Test
    void addMessageFromResidentReopensResolvedTicket() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident).subject("Leaking tap").status(SupportTicketStatus.RESOLVED).build();

        when(supportTicketRepository.findByIdAndResident(1L, resident)).thenReturn(Optional.of(ticket));
        when(supportTicketMessageRepository.findByTicketOrderByCreatedAtAsc(ticket)).thenReturn(java.util.List.of());

        SupportTicketDetailResponse response = supportTicketService.addMessageFromResident(
                1L, resident, new AddTicketMessageRequest("Still happening, please reopen."));

        assertThat(response.status()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void addMessageFromResidentThrowsWhenTicketBelongsToSomeoneElse() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        when(supportTicketRepository.findByIdAndResident(1L, resident)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportTicketService.addMessageFromResident(
                1L, resident, new AddTicketMessageRequest("hi")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateStatusToResolvedSetsResolvedAtAndNotifiesResident() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident).subject("Leaking tap").status(SupportTicketStatus.IN_PROGRESS).build();

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        SupportTicketResponse response = supportTicketService.updateStatus(1L, SupportTicketStatus.RESOLVED);

        assertThat(response.status()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        verify(emailService, times(1)).sendSupportTicketUpdateEmail(any(), any(), any(), any(), any());
    }

    @Test
    void updateStatusAwayFromResolvedClearsResolvedAt() {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident).subject("Leaking tap").status(SupportTicketStatus.RESOLVED)
                .resolvedAt(java.time.Instant.now()).build();

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        supportTicketService.updateStatus(1L, SupportTicketStatus.IN_PROGRESS);

        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void updateStatusNotifiesAdminRaiserInstead() {
        SupportTicket ticket = SupportTicket.builder().id(1L).apartment(apartment).raisedByRole(SupportTicketRole.ADMIN)
                .communityAdmin(admin).subject("Billing question").status(SupportTicketStatus.OPEN).build();

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        supportTicketService.updateStatus(1L, SupportTicketStatus.IN_PROGRESS);

        verify(emailService).sendSupportTicketUpdateEmail(
                org.mockito.ArgumentMatchers.eq(admin.getEmail()), org.mockito.ArgumentMatchers.eq(admin.getFullName()),
                any(), any(), any());
        verify(emailService, never()).sendSupportTicketUpdateEmail(
                org.mockito.ArgumentMatchers.eq("res@example.com"), any(), any(), any(), any());
    }
}

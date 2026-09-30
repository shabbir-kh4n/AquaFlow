package com.hydroledger.backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.hydroledger.backend.dto.CommunityDetailResponse;
import com.hydroledger.backend.dto.CommunitySummaryResponse;
import com.hydroledger.backend.dto.SystemStatusResponse;
import com.hydroledger.backend.entity.Announcement;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.CommunityAdminStatus;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AdminVerificationRepository;
import com.hydroledger.backend.repository.AlertConfigRepository;
import com.hydroledger.backend.repository.AnnouncementRepository;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.BulkWaterPurchaseRepository;
import com.hydroledger.backend.repository.CommonAreaConfigRepository;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.PasswordSetupTokenRepository;
import com.hydroledger.backend.repository.PushSubscriptionRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SupportTicketRepository;
import com.hydroledger.backend.repository.TariffPlanRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.repository.WaterUsageReadingRepository;
import com.hydroledger.backend.service.SystemAdminService;

@ExtendWith(MockitoExtension.class)
class SystemAdminServiceTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @Mock
    private WaterUsageReadingRepository waterUsageReadingRepository;

    @Mock
    private PasswordSetupTokenRepository passwordSetupTokenRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private TariffPlanRepository tariffPlanRepository;

    @Mock
    private AdminVerificationRepository adminVerificationRepository;

    @Mock
    private CommonAreaConfigRepository commonAreaConfigRepository;

    @Mock
    private BulkWaterPurchaseRepository bulkWaterPurchaseRepository;

    @Mock
    private BillingCycleRepository billingCycleRepository;

    @Mock
    private AlertConfigRepository alertConfigRepository;

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private SystemAdminService systemAdminService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin")
            .email("jane@example.com").status(CommunityAdminStatus.ACTIVE).build();

    private final Apartment apartment = Apartment.builder().id(10L).name("Sunrise Towers").admin(admin).build();

    @Test
    void listCommunitiesReturnsCorrectCounts() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").createdBy(admin).build();

        when(communityAdminRepository.findAll()).thenReturn(List.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));

        List<CommunitySummaryResponse> result = systemAdminService.listCommunities();

        assertThat(result).hasSize(1);
        CommunitySummaryResponse summary = result.get(0);
        assertThat(summary.adminId()).isEqualTo(1L);
        assertThat(summary.apartmentName()).isEqualTo("Sunrise Towers");
        assertThat(summary.householdCount()).isEqualTo(1);
        assertThat(summary.residentCount()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo(CommunityAdminStatus.ACTIVE);
    }

    @Test
    void getCommunityDetailThrows404ForUnknownAdmin() {
        when(communityAdminRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemAdminService.getCommunityDetail(99L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Community admin not found");
    }

    @Test
    void getCommunityDetailReturnsResidentsForAdmin() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com")
                .houseNumber("A-101").meterNumber("MTR-1").passwordSet(true).createdBy(admin).build();

        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));

        CommunityDetailResponse detail = systemAdminService.getCommunityDetail(1L);

        assertThat(detail.residents()).hasSize(1);
        assertThat(detail.residents().get(0).fullName()).isEqualTo("Res");
        assertThat(detail.apartmentName()).isEqualTo("Sunrise Towers");
    }

    @Test
    void updateStatusTransitionsAdminStatus() {
        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of());
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of());

        CommunitySummaryResponse response = systemAdminService.updateStatus(1L, CommunityAdminStatus.BLOCKED);

        assertThat(admin.getStatus()).isEqualTo(CommunityAdminStatus.BLOCKED);
        assertThat(response.status()).isEqualTo(CommunityAdminStatus.BLOCKED);
    }

    @Test
    void deleteCommunityAdminDeletesInDependencyOrder() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        WaterMeter meter = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-1").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").createdBy(admin).build();

        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(waterMeterRepository.findByApartmentOrderByMeterNumberAsc(apartment)).thenReturn(List.of(meter));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(commonAreaConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(alertConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of());
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of());
        when(supportTicketRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of());
        Announcement announcement = Announcement.builder().id(5L).apartment(apartment).title("Notice").body("Body").build();
        when(announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of(announcement));

        systemAdminService.deleteCommunityAdmin(1L);

        InOrder inOrder = Mockito.inOrder(waterUsageReadingRepository, billingCycleRepository,
                supportTicketRepository, passwordSetupTokenRepository, pushSubscriptionRepository, residentRepository,
                waterMeterRepository, householdRepository, announcementRepository, apartmentRepository,
                communityAdminRepository);

        inOrder.verify(waterUsageReadingRepository).deleteByMeterIn(List.of(meter));
        inOrder.verify(billingCycleRepository).deleteAll(List.of());
        // Support tickets have FKs to both residents and the admin (no ON DELETE CASCADE) - must be
        // deleted before either is removed, same as password-setup tokens/push subscriptions below.
        inOrder.verify(supportTicketRepository).deleteAll(List.of());
        inOrder.verify(passwordSetupTokenRepository).deleteByResident(resident);
        // Push subscriptions have an FK to residents (no ON DELETE CASCADE) - must be deleted
        // before the resident row itself, same as password-setup tokens above.
        inOrder.verify(pushSubscriptionRepository).deleteByResident(resident);
        inOrder.verify(residentRepository).deleteAll(List.of(resident));
        inOrder.verify(waterMeterRepository).deleteAll(List.of(meter));
        inOrder.verify(householdRepository).deleteAll(List.of(household));
        // Announcements have an FK to apartments (no ON DELETE CASCADE) - must be deleted before the apartment.
        inOrder.verify(announcementRepository).deleteAll(List.of(announcement));
        inOrder.verify(apartmentRepository).delete(apartment);
        inOrder.verify(communityAdminRepository).delete(admin);
    }

    @Test
    void deleteCommunityAdminHandlesEmptyCommunityWithoutError() {
        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of());
        when(waterMeterRepository.findByApartmentOrderByMeterNumberAsc(apartment)).thenReturn(List.of());
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of());
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(commonAreaConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(alertConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of());
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of());
        when(supportTicketRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of());
        when(announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of());

        systemAdminService.deleteCommunityAdmin(1L);

        Mockito.verify(communityAdminRepository).delete(admin);
    }

    @Test
    void getSystemStatusReportsDatabaseUpWhenConnectionValid() throws SQLException {
        ReflectionTestUtils.setField(systemAdminService, "appVersion", "1.2.3");
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        SystemStatusResponse status = systemAdminService.getSystemStatus();

        assertThat(status.databaseUp()).isTrue();
        assertThat(status.appVersion()).isEqualTo("1.2.3");
    }

    @Test
    void getSystemStatusReportsDatabaseDownOnSqlException() throws SQLException {
        ReflectionTestUtils.setField(systemAdminService, "appVersion", "1.2.3");
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        SystemStatusResponse status = systemAdminService.getSystemStatus();

        assertThat(status.databaseUp()).isFalse();
    }
}

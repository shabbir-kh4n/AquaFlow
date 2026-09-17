package com.hydroledger.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.BillEstimateResponse;
import com.hydroledger.backend.dto.ResidentDashboardResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.service.AlertConfigService;
import com.hydroledger.backend.service.AlertEngineService;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.BillingCycleService;
import com.hydroledger.backend.service.BulkWaterPurchaseService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatTool;
import com.hydroledger.backend.service.ChatToolRegistry;
import com.hydroledger.backend.service.CommonAreaConfigService;
import com.hydroledger.backend.service.HouseholdService;
import com.hydroledger.backend.service.PlatformReportService;
import com.hydroledger.backend.service.ResidentDashboardService;
import com.hydroledger.backend.service.SystemAdminService;
import com.hydroledger.backend.service.SystemMetricsService;
import com.hydroledger.backend.service.TariffPlanService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class ChatToolRegistryTests {

    @Mock
    private ApartmentService apartmentService;

    @Mock
    private ResidentDashboardService residentDashboardService;

    @Mock
    private TariffPlanService tariffPlanService;

    @Mock
    private BillingCycleService billingCycleService;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @Mock
    private AlertEngineService alertEngineService;

    @Mock
    private HouseholdService householdService;

    @Mock
    private CommonAreaConfigService commonAreaConfigService;

    @Mock
    private BulkWaterPurchaseService bulkWaterPurchaseService;

    @Mock
    private AlertConfigService alertConfigService;

    @Mock
    private SystemAdminService systemAdminService;

    @Mock
    private SystemMetricsService systemMetricsService;

    @Mock
    private PlatformReportService platformReportService;

    private ChatToolRegistry chatToolRegistry;

    private final Resident resident = Resident.builder().id(7L).fullName("Res Ident").email("res@example.com").build();
    private final CommunityAdmin admin = CommunityAdmin.builder().id(3L).fullName("Admin").email("admin@example.com").build();
    private final Apartment apartment = Apartment.builder().id(9L).name("Green Valley").build();
    private final SystemAdmin systemAdmin = SystemAdmin.builder().id(1L).fullName("Sys Admin").email("sysadmin@hydroledger.local").build();

    @BeforeEach
    void setUp() {
        chatToolRegistry = new ChatToolRegistry(apartmentService, residentDashboardService, tariffPlanService,
                billingCycleService, waterUsageReadingService, alertEngineService, householdService,
                commonAreaConfigService, bulkWaterPurchaseService, alertConfigService, systemAdminService,
                systemMetricsService, platformReportService);
    }

    @Test
    void residentPersonaExposesExpectedTools() {
        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident);

        assertThat(tools.keySet()).containsExactlyInAnyOrder(
                "get_dashboard_summary", "get_bill_estimate", "list_my_invoices",
                "list_my_usage_history", "list_my_alerts");
    }

    @Test
    void adminPersonaExposesExpectedTools() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.ADMIN, admin);

        assertThat(tools.keySet()).containsExactlyInAnyOrder(
                "list_households", "list_billing_cycles", "get_billing_cycle_detail", "get_tariff_plan",
                "get_common_area_config", "list_bulk_purchases", "list_alerts", "get_alert_config");
    }

    @Test
    void platformPersonaExposesExpectedTools() {
        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.PLATFORM, systemAdmin);

        assertThat(tools.keySet()).containsExactlyInAnyOrder(
                "list_communities", "get_community_detail", "get_system_status",
                "get_system_metrics", "get_platform_report");
    }

    @Test
    void landingPersonaGetsNoToolsYet() {
        assertThat(chatToolRegistry.toolsFor(ChatPersona.LANDING, null)).isEmpty();
    }

    @Test
    void getCommunityDetailAcceptsAModelSuppliedAdminId() {
        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.PLATFORM, systemAdmin).get("get_community_detail");
        tool.execute(Map.of("adminId", 17));

        verify(systemAdminService).getCommunityDetail(17L);
    }

    @Test
    void listCommunitiesAndSystemStatusDelegateToSystemAdminService() {
        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.PLATFORM, systemAdmin);
        tools.get("list_communities").execute(Map.of());
        tools.get("get_system_status").execute(Map.of());

        verify(systemAdminService).listCommunities();
        verify(systemAdminService).getSystemStatus();
    }

    @Test
    void getSystemMetricsAndPlatformReportDelegateWithGivenArgs() {
        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.PLATFORM, systemAdmin);
        tools.get("get_system_metrics").execute(Map.of("range", "1h"));
        tools.get("get_platform_report").execute(Map.of("range", "3mo"));

        verify(systemMetricsService).getMetrics("1h");
        verify(platformReportService).getReport("3mo", null, null);
    }

    @Test
    void getDashboardSummaryDelegatesToServiceWithTheAuthenticatedResident() {
        when(residentDashboardService.getDashboard(resident))
                .thenReturn(new ResidentDashboardResponse("Green Valley", "A-101", "M-1", null, null));

        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("get_dashboard_summary");
        Object result = tool.execute(Map.of());

        assertThat(result).isInstanceOf(ResidentDashboardResponse.class);
        verify(residentDashboardService).getDashboard(resident);
    }

    @Test
    void getBillEstimateDefaultsToCurrentMonthAndIgnoresUnrelatedArgs() {
        when(tariffPlanService.estimateForResident(eq(resident), any()))
                .thenReturn(new BillEstimateResponse("2026-09", null, null, null, null, null, null, null, null));

        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("get_bill_estimate");
        tool.execute(Map.of());

        verify(tariffPlanService).estimateForResident(resident, YearMonth.now());
    }

    @Test
    void getBillEstimateHonoursAnExplicitMonthArgument() {
        when(tariffPlanService.estimateForResident(eq(resident), any()))
                .thenReturn(new BillEstimateResponse("2026-01", null, null, null, null, null, null, null, null));

        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("get_bill_estimate");
        tool.execute(Map.of("yearMonth", "2026-01"));

        verify(tariffPlanService).estimateForResident(resident, YearMonth.of(2026, 1));
    }

    @Test
    void listMyInvoicesDelegatesWithTheAuthenticatedResidentOnly() {
        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("list_my_invoices");
        tool.execute(Map.of());

        verify(billingCycleService).listInvoicesForResident(resident);
    }

    @Test
    void listMyUsageHistoryDelegatesWithTheAuthenticatedResidentOnly() {
        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("list_my_usage_history");
        tool.execute(Map.of());

        verify(waterUsageReadingService).listForResident(resident);
    }

    @Test
    void listMyAlertsDelegatesWithTheAuthenticatedResidentOnly() {
        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.RESIDENT, resident).get("list_my_alerts");
        tool.execute(Map.of());

        verify(alertEngineService).listForResident(resident);
    }

    @Test
    void adminToolsResolveApartmentFromTheAuthenticatedAdminOnce() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.ADMIN, admin);
        tools.get("list_households").execute(Map.of());
        tools.get("get_tariff_plan").execute(Map.of());

        verify(apartmentService, org.mockito.Mockito.times(1)).getOrCreate(admin);
        verify(householdService).listForAdmin(apartment);
        verify(tariffPlanService).getOrDefault(apartment);
    }

    @Test
    void getBillingCycleDetailUsesTheAdminsOwnApartmentAndTheGivenCycleId() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        ChatTool tool = chatToolRegistry.toolsFor(ChatPersona.ADMIN, admin).get("get_billing_cycle_detail");
        tool.execute(Map.of("billingCycleId", 42));

        verify(billingCycleService).getDetail(apartment, 42L);
    }

    @Test
    void listAlertsAndListBulkPurchasesAndCommonAreaConfigDelegateWithTheAdminsApartment() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        Map<String, ChatTool> tools = chatToolRegistry.toolsFor(ChatPersona.ADMIN, admin);
        tools.get("list_alerts").execute(Map.of());
        tools.get("list_bulk_purchases").execute(Map.of());
        tools.get("get_common_area_config").execute(Map.of());
        tools.get("get_alert_config").execute(Map.of());
        tools.get("list_billing_cycles").execute(Map.of());

        verify(alertEngineService).listAlerts(apartment, null);
        verify(bulkWaterPurchaseService).list(apartment);
        verify(commonAreaConfigService).getOrDefault(apartment);
        verify(alertConfigService).getOrDefault(apartment);
        verify(billingCycleService).list(apartment);
    }
}

package com.hydroledger.backend.service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;

import lombok.RequiredArgsConstructor;

/**
 * Builds the persona-scoped tool map for a single chat request. Every tool
 * closure below captures the already-authenticated principal resolved by
 * Spring Security - it never accepts an apartment/household/resident id from
 * Gemini's function-call {@code args} for the Resident/Admin personas. See
 * AGENTS.md / the chat assistant plan for why this boundary matters.
 */
@Service
@RequiredArgsConstructor
public class ChatToolRegistry {

    private final ApartmentService apartmentService;
    private final ResidentDashboardService residentDashboardService;
    private final TariffPlanService tariffPlanService;
    private final BillingCycleService billingCycleService;
    private final WaterUsageReadingService waterUsageReadingService;
    private final AlertEngineService alertEngineService;
    private final HouseholdService householdService;
    private final CommonAreaConfigService commonAreaConfigService;
    private final BulkWaterPurchaseService bulkWaterPurchaseService;
    private final AlertConfigService alertConfigService;
    private final SystemAdminService systemAdminService;
    private final SystemMetricsService systemMetricsService;
    private final PlatformReportService platformReportService;

    public Map<String, ChatTool> toolsFor(ChatPersona persona, Object principal) {
        Map<String, ChatTool> tools = new LinkedHashMap<>();
        if (persona == ChatPersona.RESIDENT && principal instanceof Resident resident) {
            registerResidentTools(tools, resident);
        } else if (persona == ChatPersona.ADMIN && principal instanceof CommunityAdmin admin) {
            registerAdminTools(tools, apartmentService.getOrCreate(admin));
        } else if (persona == ChatPersona.PLATFORM && principal instanceof SystemAdmin) {
            registerPlatformTools(tools);
        }
        // LANDING's tool set is added in a later rollout step.
        return tools;
    }

    private void registerResidentTools(Map<String, ChatTool> tools, Resident resident) {
        tools.put("get_dashboard_summary", noArgTool(
                "get_dashboard_summary",
                "Get the resident's dashboard summary: community name, house/meter number, and latest meter reading.",
                () -> residentDashboardService.getDashboard(resident)));

        tools.put("get_bill_estimate", yearMonthTool(
                "get_bill_estimate",
                "Get the resident's tiered-tariff bill estimate for a given month (defaults to the current month).",
                month -> tariffPlanService.estimateForResident(resident, month)));

        tools.put("list_my_invoices", noArgTool(
                "list_my_invoices",
                "List all of the resident's own finalized invoices, newest first.",
                () -> billingCycleService.listInvoicesForResident(resident)));

        tools.put("list_my_usage_history", noArgTool(
                "list_my_usage_history",
                "List all of the resident's own recorded water meter readings, oldest to newest.",
                () -> waterUsageReadingService.listForResident(resident)));

        tools.put("list_my_alerts", noArgTool(
                "list_my_alerts",
                "List the resident's own usage/leak alerts (threshold-exceeded or statistical outlier).",
                () -> alertEngineService.listForResident(resident)));
    }

    private void registerAdminTools(Map<String, ChatTool> tools, Apartment apartment) {
        tools.put("list_households", noArgTool(
                "list_households",
                "List every household/resident registered in this admin's own community.",
                () -> householdService.listForAdmin(apartment)));

        tools.put("list_billing_cycles", noArgTool(
                "list_billing_cycles",
                "List this community's billing cycles (open/finalized/archived), with totals.",
                () -> billingCycleService.list(apartment)));

        tools.put("get_billing_cycle_detail", new ChatTool() {
            @Override
            public String name() {
                return "get_billing_cycle_detail";
            }

            @Override
            public String description() {
                return "Get the per-household invoice breakdown for one of this community's billing cycles.";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of(
                        "billingCycleId", Map.of("type", "INTEGER", "description", "The billing cycle's id")),
                        "required", java.util.List.of("billingCycleId"));
            }

            @Override
            public Object execute(Map<String, Object> args) {
                Number id = (Number) args.get("billingCycleId");
                return billingCycleService.getDetail(apartment, id.longValue());
            }
        });

        tools.put("get_tariff_plan", noArgTool(
                "get_tariff_plan",
                "Get this community's tiered tariff plan (rate per usage tier).",
                () -> tariffPlanService.getOrDefault(apartment)));

        tools.put("get_common_area_config", noArgTool(
                "get_common_area_config",
                "Get this community's shared common-area cost configuration and distribution method.",
                () -> commonAreaConfigService.getOrDefault(apartment)));

        tools.put("list_bulk_purchases", noArgTool(
                "list_bulk_purchases",
                "List this community's recorded bulk water purchases (tanker deliveries/municipal supply).",
                () -> bulkWaterPurchaseService.list(apartment)));

        tools.put("list_alerts", noArgTool(
                "list_alerts",
                "List this community's usage/leak alerts across all households.",
                () -> alertEngineService.listAlerts(apartment, null)));

        tools.put("get_alert_config", noArgTool(
                "get_alert_config",
                "Get this community's alert engine configuration (threshold, outlier sensitivity, enable flags).",
                () -> alertConfigService.getOrDefault(apartment)));
    }

    private void registerPlatformTools(Map<String, ChatTool> tools) {
        tools.put("list_communities", noArgTool(
                "list_communities",
                "List every registered community/admin on the platform with basic counts.",
                systemAdminService::listCommunities));

        // The one intentional exception to "never trust a model-supplied id": system admin
        // is platform-wide by design, so cross-community lookups by id are legitimate here -
        // do not copy this parameter pattern into resident/admin tools above.
        tools.put("get_community_detail", new ChatTool() {
            @Override
            public String name() {
                return "get_community_detail";
            }

            @Override
            public String description() {
                return "Get one community's detail (admin info, apartment, resident list) by its admin id"
                        + " (from list_communities' adminId field).";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of(
                        "adminId", Map.of("type", "INTEGER", "description", "The community admin's id")),
                        "required", java.util.List.of("adminId"));
            }

            @Override
            public Object execute(Map<String, Object> args) {
                Number adminId = (Number) args.get("adminId");
                return systemAdminService.getCommunityDetail(adminId.longValue());
            }
        });

        tools.put("get_system_status", noArgTool(
                "get_system_status",
                "Get basic backend/DB system status (connectivity, uptime, version).",
                systemAdminService::getSystemStatus));

        tools.put("get_system_metrics", new ChatTool() {
            @Override
            public String name() {
                return "get_system_metrics";
            }

            @Override
            public String description() {
                return "Get platform request-rate/latency/connection metrics for a time range.";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of(
                        "range", Map.of("type", "STRING",
                                "description", "One of 5m, 15m, 1h, 1d, 1w, 1mo, 3mo")),
                        "required", java.util.List.of("range"));
            }

            @Override
            public Object execute(Map<String, Object> args) {
                String range = (String) args.getOrDefault("range", "1h");
                return systemMetricsService.getMetrics(range);
            }
        });

        tools.put("get_platform_report", new ChatTool() {
            @Override
            public String name() {
                return "get_platform_report";
            }

            @Override
            public String description() {
                return "Get platform-wide usage/billing report: per-community and overall totals, and a monthly trend."
                        + " Use `range` (3mo/6mo/12mo) for a preset window, or `from`/`to` (YYYY-MM) for a custom one.";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of(
                        "range", Map.of("type", "STRING", "description", "One of 3mo, 6mo, 12mo - optional"),
                        "from", Map.of("type", "STRING", "description", "YYYY-MM, optional, pairs with `to`"),
                        "to", Map.of("type", "STRING", "description", "YYYY-MM, optional, pairs with `from`")));
            }

            @Override
            public Object execute(Map<String, Object> args) {
                String range = args != null ? (String) args.get("range") : null;
                String from = args != null ? (String) args.get("from") : null;
                String to = args != null ? (String) args.get("to") : null;
                return platformReportService.getReport(range, from, to);
            }
        });
    }

    private ChatTool noArgTool(String name, String description, Supplier<Object> executor) {
        return new ChatTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of());
            }

            @Override
            public Object execute(Map<String, Object> args) {
                return executor.get();
            }
        };
    }

    private ChatTool yearMonthTool(String name, String description, Function<YearMonth, Object> executor) {
        return new ChatTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of(
                        "yearMonth", Map.of("type", "STRING", "description", "Format YYYY-MM, optional")));
            }

            @Override
            public Object execute(Map<String, Object> args) {
                YearMonth month = args != null && args.get("yearMonth") != null
                        ? YearMonth.parse((String) args.get("yearMonth"))
                        : YearMonth.now();
                return executor.apply(month);
            }
        };
    }
}

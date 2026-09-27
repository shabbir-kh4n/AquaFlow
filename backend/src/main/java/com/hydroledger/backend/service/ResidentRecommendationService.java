package com.hydroledger.backend.service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.AlertResponse;
import com.hydroledger.backend.dto.BillEstimateResponse;
import com.hydroledger.backend.dto.ResidentRecommendationResponse;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.AlertStatus;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.ResidentRecommendation;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRecommendationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Generates a short list of personalized, Gemini-written water-saving tips for a resident,
 * grounded in that resident's own usage/bill/alert data (same data surface as
 * ChatToolRegistry's resident tools). Unlike the chat assistant, this is a single one-shot
 * generateContent call - no function-calling loop, no multi-turn conversation.
 *
 * Tips are generated once per billing cycle (triggered from BillingCycleService.finalizeCycle)
 * and persisted, so the resident-facing page never waits on a live Gemini call. A resident with
 * no finalized cycle yet gets a one-time fallback generation via getForResident below.
 */
@Service
@RequiredArgsConstructor
public class ResidentRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(ResidentRecommendationService.class);

    private static final String SYSTEM_INSTRUCTION =
            "You are HydroLedger's water-saving advisor. Given a resident's own usage, billing, and "
                    + "alert data, write 3 to 5 short, concrete, personalized water-saving tips. "
                    + "Reference the resident's actual numbers where relevant (their tier, their trend, "
                    + "any active alerts). One tip per line, no numbering, no markdown, no preamble.";

    private final GeminiClient geminiClient;
    private final ResidentRecommendationRepository residentRecommendationRepository;
    private final WaterUsageReadingService waterUsageReadingService;
    private final TariffPlanService tariffPlanService;
    private final InvoiceRepository invoiceRepository;
    private final AlertEngineService alertEngineService;

    /**
     * Returns the resident's persisted tips in {@code requestedLanguage} (the frontend's selected UI
     * language). If nothing is persisted yet, or the persisted tips are in a different language, tips
     * are generated synchronously in that language first - a one-time wait on the resident's own page
     * load is acceptable, unlike billing-cycle finalize which must never block on Gemini. The new
     * language is persisted, so later per-cycle regenerations keep using it.
     */
    public ResidentRecommendationResponse getForResident(Resident resident, String requestedLanguage) {
        String language = ReplyLanguage.normalize(requestedLanguage);
        Optional<ResidentRecommendation> existing = residentRecommendationRepository.findByResident(resident);
        if (existing.isPresent() && language.equals(existing.get().getLanguage())) {
            return toResponse(existing.get());
        }

        String periodMonth = existing.map(ResidentRecommendation::getSourcePeriodMonth).orElse(null);
        generateForResident(resident, periodMonth, language);
        // On a Gemini failure this falls back to whatever was already persisted (possibly in the
        // previous language) rather than showing the resident an empty page.
        return residentRecommendationRepository.findByResident(resident)
                .map(this::toResponse)
                .orElse(new ResidentRecommendationResponse(List.of(), null, null));
    }

    /**
     * Fire-and-forget entry point for BillingCycleService.finalizeCycle. Generation involves a
     * live Gemini call per household, which must never stall bill finalization - a slow/stalled
     * call here previously blocked the whole finalize request. Runs on the pool configured in
     * AsyncConfig; any failure is still caught and logged inside generateForResident itself.
     */
    @Async
    public void generateForResidentAsync(Resident resident, String periodMonth) {
        generateForResident(resident, periodMonth);
    }

    /** Regenerates in whatever language the resident's tips were last generated in (English if none). */
    @Transactional
    public void generateForResident(Resident resident, String periodMonth) {
        String language = residentRecommendationRepository.findByResident(resident)
                .map(ResidentRecommendation::getLanguage)
                .orElse(ReplyLanguage.DEFAULT);
        generateForResident(resident, periodMonth, language);
    }

    @Transactional
    public void generateForResident(Resident resident, String periodMonth, String language) {
        try {
            String prompt = buildPrompt(resident, periodMonth);
            GeminiTurnResult result = geminiClient.generateContent(
                    SYSTEM_INSTRUCTION + ReplyLanguage.instructionFor(language),
                    List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))), null);

            if (!(result instanceof GeminiTurnResult.FinalText finalText)) {
                log.warn("Gemini returned a non-text result generating recommendations for resident {}", resident.getId());
                return;
            }

            List<String> tips = parseTips(finalText.text());
            if (tips.isEmpty()) {
                log.warn("Gemini returned no usable tips for resident {}", resident.getId());
                return;
            }

            ResidentRecommendation recommendation = residentRecommendationRepository.findByResident(resident)
                    .orElseGet(() -> ResidentRecommendation.builder().resident(resident).build());
            recommendation.setTips(String.join("\n", tips));
            recommendation.setGeneratedAt(Instant.now());
            recommendation.setSourcePeriodMonth(periodMonth);
            recommendation.setLanguage(ReplyLanguage.normalize(language));
            residentRecommendationRepository.save(recommendation);
        } catch (Exception ex) {
            // A Gemini outage/failure must never break billing-cycle finalize (the primary caller)
            // or the fallback path in getForResident - log and leave any existing tips in place.
            log.error("Failed to generate recommendations for resident {}", resident.getId(), ex);
        }
    }

    private String buildPrompt(Resident resident, String periodMonth) {
        YearMonth month = periodMonth != null ? YearMonth.parse(periodMonth) : YearMonth.now();

        BillEstimateResponse estimate = tariffPlanService.estimateForResident(resident, month);
        List<WaterUsageReadingResponse> readings = waterUsageReadingService.listForResident(resident);
        Household household = resident.getHousehold();
        List<Invoice> invoices = household != null
                ? invoiceRepository.findByHouseholdOrderByGeneratedAtDesc(household)
                : List.of();
        List<AlertResponse> activeAlerts = alertEngineService.listForResident(resident).stream()
                .filter(alert -> alert.status() == AlertStatus.ACTIVE)
                .toList();

        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
        for (WaterUsageReadingResponse reading : readings) {
            monthlyTotals.merge(reading.consumptionMonth(), reading.consumption(), BigDecimal::add);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Resident: ").append(resident.getFullName()).append('\n');
        prompt.append("Current period (").append(estimate.periodMonth()).append("): ")
                .append(estimate.consumptionUnits()).append(" kL consumed, base charge ₹")
                .append(estimate.baseAmount()).append(", tariff charge ₹").append(estimate.tariffAmount())
                .append(", common area charge ₹").append(estimate.commonAreaAmount())
                .append(", total estimated bill ₹").append(estimate.totalAmount()).append('\n');

        if (!estimate.tierBreakdown().isEmpty()) {
            String highestTier = estimate.tierBreakdown().stream()
                    .filter(line -> line.unitsInTier().compareTo(BigDecimal.ZERO) > 0)
                    .reduce((first, second) -> second)
                    .map(line -> "Tier " + line.tierOrder())
                    .orElse("Tier 1");
            prompt.append("Highest tariff tier reached this period: ").append(highestTier).append('\n');
        }

        if (monthlyTotals.size() >= 2) {
            List<String> months = new ArrayList<>(monthlyTotals.keySet());
            String previous = months.get(months.size() - 2);
            String latest = months.get(months.size() - 1);
            prompt.append("Consumption trend: ").append(previous).append(" = ").append(monthlyTotals.get(previous))
                    .append(" kL, ").append(latest).append(" = ").append(monthlyTotals.get(latest)).append(" kL\n");
        }

        if (!invoices.isEmpty()) {
            Invoice latest = invoices.get(0);
            prompt.append("Most recent finalized invoice (").append(latest.getBillingCycle().getPeriodMonth()).append("): ₹")
                    .append(latest.getTotalAmount()).append(latest.isPaid() ? " (paid)" : " (unpaid)").append('\n');
        }

        if (activeAlerts.isEmpty()) {
            prompt.append("No active usage alerts.\n");
        } else {
            prompt.append("Active alerts:\n");
            for (AlertResponse alert : activeAlerts) {
                prompt.append("- ").append(alert.message()).append('\n');
            }
        }

        return prompt.toString();
    }

    private List<String> parseTips(String text) {
        List<String> tips = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String cleaned = line.strip().replaceFirst("^[-*•\\d.)\\s]+", "").strip();
            if (!cleaned.isEmpty()) {
                tips.add(cleaned);
            }
        }
        return tips;
    }

    private ResidentRecommendationResponse toResponse(ResidentRecommendation recommendation) {
        List<String> tips = recommendation.getTips() == null || recommendation.getTips().isBlank()
                ? List.of()
                : List.of(recommendation.getTips().split("\n"));
        return new ResidentRecommendationResponse(tips, recommendation.getGeneratedAt(), recommendation.getSourcePeriodMonth());
    }
}

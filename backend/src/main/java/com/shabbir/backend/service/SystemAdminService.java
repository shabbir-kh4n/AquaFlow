package com.shabbir.backend.service;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.CommunityDetailResponse;
import com.shabbir.backend.dto.CommunitySummaryResponse;
import com.shabbir.backend.dto.SystemStatusResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.CommunityAdminStatus;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.AlertConfigRepository;
import com.shabbir.backend.repository.ApartmentRepository;
import com.shabbir.backend.repository.BillingCycleRepository;
import com.shabbir.backend.repository.BulkWaterPurchaseRepository;
import com.shabbir.backend.repository.CommonAreaConfigRepository;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.HouseholdRepository;
import com.shabbir.backend.repository.PasswordSetupTokenRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.TariffPlanRepository;
import com.shabbir.backend.repository.WaterMeterRepository;
import com.shabbir.backend.repository.WaterUsageReadingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemAdminService {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminService.class);

    private final CommunityAdminRepository communityAdminRepository;
    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final ResidentRepository residentRepository;
    private final WaterMeterRepository waterMeterRepository;
    private final WaterUsageReadingRepository waterUsageReadingRepository;
    private final PasswordSetupTokenRepository passwordSetupTokenRepository;
    private final TariffPlanRepository tariffPlanRepository;
    private final CommonAreaConfigRepository commonAreaConfigRepository;
    private final BulkWaterPurchaseRepository bulkWaterPurchaseRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final DataSource dataSource;

    @Value("${app.version}")
    private String appVersion;

    public List<CommunitySummaryResponse> listCommunities() {
        return communityAdminRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    private CommunitySummaryResponse toSummary(CommunityAdmin admin) {
        Optional<Apartment> apartment = apartmentRepository.findByAdmin(admin);
        int householdCount = apartment.map(a -> householdRepository.findByApartmentOrderByHouseNumberAsc(a).size()).orElse(0);
        int residentCount = residentRepository.findByCreatedByOrderByCreatedAtDesc(admin).size();

        return new CommunitySummaryResponse(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getStatus(),
                apartment.map(Apartment::getId).orElse(null),
                apartment.map(Apartment::getName).orElse(null),
                householdCount,
                residentCount,
                admin.getCreatedAt()
        );
    }

    public CommunityDetailResponse getCommunityDetail(Long adminId) {
        CommunityAdmin admin = requireAdmin(adminId);
        Optional<Apartment> apartment = apartmentRepository.findByAdmin(admin);

        List<CommunityDetailResponse.ResidentSummary> residents = residentRepository
                .findByCreatedByOrderByCreatedAtDesc(admin).stream()
                .map(r -> new CommunityDetailResponse.ResidentSummary(
                        r.getId(), r.getFullName(), r.getEmail(), r.getHouseNumber(), r.getMeterNumber(), r.isPasswordSet()))
                .toList();

        return new CommunityDetailResponse(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getStatus(),
                apartment.map(Apartment::getId).orElse(null),
                apartment.map(Apartment::getName).orElse(null),
                apartment.map(Apartment::getAddress).orElse(null),
                admin.getCreatedAt(),
                residents
        );
    }

    @Transactional
    public CommunitySummaryResponse updateStatus(Long adminId, CommunityAdminStatus status) {
        CommunityAdmin admin = requireAdmin(adminId);
        admin.setStatus(status);
        return toSummary(admin);
    }

    @Transactional
    public void deleteCommunityAdmin(Long adminId) {
        CommunityAdmin admin = requireAdmin(adminId);
        Optional<Apartment> apartmentOpt = apartmentRepository.findByAdmin(admin);

        if (apartmentOpt.isEmpty()) {
            communityAdminRepository.delete(admin);
            return;
        }

        Apartment apartment = apartmentOpt.get();

        List<Household> households = householdRepository.findByApartmentOrderByHouseNumberAsc(apartment);
        List<WaterMeter> meters = waterMeterRepository.findByApartmentOrderByMeterNumberAsc(apartment);
        List<Resident> residents = residentRepository.findByCreatedByOrderByCreatedAtDesc(admin);

        waterUsageReadingRepository.deleteByMeterIn(meters);

        // Invoices reference households (no ON DELETE CASCADE on that FK, only on billing_cycle_id),
        // so billing cycles must be deleted before households/meters, not after.
        billingCycleRepository.deleteAll(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment));

        residents.forEach(passwordSetupTokenRepository::deleteByResident);
        residentRepository.deleteAll(residents);
        waterMeterRepository.deleteAll(meters);
        householdRepository.deleteAll(households);

        tariffPlanRepository.findByApartment(apartment).ifPresent(tariffPlanRepository::delete);
        commonAreaConfigRepository.findByApartment(apartment).ifPresent(commonAreaConfigRepository::delete);
        alertConfigRepository.findByApartment(apartment).ifPresent(alertConfigRepository::delete);
        bulkWaterPurchaseRepository.deleteAll(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment));

        apartmentRepository.delete(apartment);
        communityAdminRepository.delete(admin);
    }

    public SystemStatusResponse getSystemStatus() {
        boolean databaseUp;
        try (Connection connection = dataSource.getConnection()) {
            databaseUp = connection.isValid(2);
        } catch (SQLException ex) {
            log.error("System status DB check failed", ex);
            databaseUp = false;
        }

        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        return new SystemStatusResponse(databaseUp, uptimeMillis, appVersion);
    }

    private CommunityAdmin requireAdmin(Long adminId) {
        return communityAdminRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Community admin not found"));
    }
}

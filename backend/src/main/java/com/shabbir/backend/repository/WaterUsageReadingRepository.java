package com.shabbir.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.entity.WaterUsageReading;

public interface WaterUsageReadingRepository extends JpaRepository<WaterUsageReading, Long> {

    boolean existsByMeterAndReadingDate(WaterMeter meter, LocalDate readingDate);

    List<WaterUsageReading> findByMeterOrderByReadingDateDesc(WaterMeter meter);

    List<WaterUsageReading> findByMeterOrderByReadingDateAsc(WaterMeter meter);

    Optional<WaterUsageReading> findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(
            WaterMeter meter, LocalDate readingDate);

    @Query("""
            SELECT r FROM WaterUsageReading r
            WHERE r.meter.apartment = :apartment
            AND (:meterNumber IS NULL OR r.meter.meterNumber = :meterNumber)
            AND (:from IS NULL OR r.readingDate >= :from)
            AND (:to IS NULL OR r.readingDate <= :to)
            ORDER BY r.readingDate DESC
            """)
    List<WaterUsageReading> search(
            @Param("apartment") Apartment apartment,
            @Param("meterNumber") String meterNumber,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}

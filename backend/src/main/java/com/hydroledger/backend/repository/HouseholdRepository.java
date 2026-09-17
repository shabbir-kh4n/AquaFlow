package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;

public interface HouseholdRepository extends JpaRepository<Household, Long> {

    Optional<Household> findByApartmentAndHouseNumber(Apartment apartment, String houseNumber);

    List<Household> findByApartmentOrderByHouseNumberAsc(Apartment apartment);

    @Query("SELECT h FROM Household h JOIN FETCH h.apartment WHERE h.id = :id")
    Optional<Household> findWithApartmentById(@Param("id") Long id);
}

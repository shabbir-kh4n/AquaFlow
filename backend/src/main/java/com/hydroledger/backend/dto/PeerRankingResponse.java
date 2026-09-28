package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where the caller's household ranks against every other household in its apartment for the
 * latest month the caller has consumption data. Lower consumption = better (rank 1).
 *
 * PRIVACY: this is returned to a resident, so it deliberately carries no household ids, house
 * numbers or resident names for anyone - neighbours are only a rank and a consumption value.
 *
 * All fields other than {@code totalHouseholds}/{@code entries} are null when there's no data.
 */
public record PeerRankingResponse(
        String month,
        Integer rank,
        int totalHouseholds,
        // Share (0-100) of the *other* households that used strictly more water than the caller.
        // Null when the caller is the only household with data (nobody to compare against).
        BigDecimal percentile,
        BigDecimal householdConsumption,
        BigDecimal apartmentMin,
        BigDecimal apartmentMedian,
        BigDecimal apartmentMax,
        List<Entry> entries
) {
    public record Entry(int rank, BigDecimal consumption, boolean self) {
    }

    public static PeerRankingResponse empty() {
        return new PeerRankingResponse(null, null, 0, null, null, null, null, null, List.of());
    }
}

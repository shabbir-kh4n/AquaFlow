-- Data migration: derive Apartment/Household/WaterMeter records for community admins and
-- residents that existed before this schema was introduced, then backfill residents.household_id.
--
-- Verified against the live local Postgres dev DB on 2026-08-31 via:
--   SELECT admin_id, house_number, meter_number FROM residents ORDER BY admin_id;
-- No anomalies found (no meter_number reused under two different house_numbers for the same
-- admin), so the "latest created_at wins" tiebreak below never has to arbitrate in this dataset.

-- 1) One default Apartment per CommunityAdmin that doesn't already have one.
INSERT INTO apartments (name, admin_id, created_at)
SELECT ca.full_name || '''s Apartment', ca.id, now()
FROM community_admins ca
WHERE NOT EXISTS (SELECT 1 FROM apartments a WHERE a.admin_id = ca.id);

-- 2) Distinct Households from existing residents.house_number, grouped per apartment.
INSERT INTO households (apartment_id, house_number, created_at)
SELECT DISTINCT a.id, r.house_number, now()
FROM residents r
JOIN apartments a ON a.admin_id = r.admin_id
WHERE r.house_number IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM households h
        WHERE h.apartment_id = a.id AND h.house_number = r.house_number
  );

-- 3) Distinct WaterMeters from non-null residents.meter_number, linked to the implied household.
-- If the same meter_number ever appears under two different house_numbers for one admin, the
-- resident row with the latest created_at (id as tiebreak) wins.
WITH latest_meter_resident AS (
    SELECT DISTINCT ON (r.admin_id, r.meter_number)
        r.admin_id, r.meter_number, r.house_number
    FROM residents r
    WHERE r.meter_number IS NOT NULL AND r.meter_number <> ''
    ORDER BY r.admin_id, r.meter_number, r.created_at DESC, r.id DESC
)
INSERT INTO water_meters (apartment_id, meter_number, household_id, created_at)
SELECT a.id, lmr.meter_number, h.id, now()
FROM latest_meter_resident lmr
JOIN apartments a ON a.admin_id = lmr.admin_id
JOIN households h ON h.apartment_id = a.id AND h.house_number = lmr.house_number
WHERE NOT EXISTS (
        SELECT 1 FROM water_meters wm
        WHERE wm.apartment_id = a.id AND wm.meter_number = lmr.meter_number
);

-- 4) Backfill residents.household_id.
UPDATE residents r
SET household_id = h.id
FROM apartments a
JOIN households h ON h.apartment_id = a.id
WHERE a.admin_id = r.admin_id
  AND h.house_number = r.house_number
  AND r.household_id IS NULL;

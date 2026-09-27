-- Language (ISO 639 code) the persisted tips were generated in. Existing rows were all
-- generated in English.
ALTER TABLE resident_recommendations
    ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'en';

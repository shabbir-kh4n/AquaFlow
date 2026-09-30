-- Community details submitted alongside the verification document. Nullable at the DB level so any row
-- created before this migration stays valid; the service requires them on every (re)submission.
ALTER TABLE admin_verifications
    ADD COLUMN community_name VARCHAR(200),
    ADD COLUMN address        VARCHAR(500),
    ADD COLUMN city           VARCHAR(100),
    ADD COLUMN state          VARCHAR(100),
    ADD COLUMN pincode        VARCHAR(12),
    ADD COLUMN total_flats    INTEGER,
    ADD COLUMN total_blocks   INTEGER;

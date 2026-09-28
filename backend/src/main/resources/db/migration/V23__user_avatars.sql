-- Preset avatar selection for all three account types. Keys look like 'resident-07',
-- 'admin-03', 'system-12' and map to frontend/public/avatars/{set}/{nn}.svg. NULL means
-- the UI falls back to the user's initials.
ALTER TABLE residents ADD COLUMN avatar_key VARCHAR(32);
ALTER TABLE community_admins ADD COLUMN avatar_key VARCHAR(32);
ALTER TABLE system_admins ADD COLUMN avatar_key VARCHAR(32);

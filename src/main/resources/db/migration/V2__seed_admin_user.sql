-- Creates the administrator row with a deliberately UNUSABLE password placeholder.
--
-- '!' is not a valid BCrypt hash, so BCryptPasswordEncoder.matches() can never
-- succeed against it and this row cannot be authenticated against as shipped.
-- AdminAccountInitializer replaces it with a real hash derived from the
-- ADMIN_PASSWORD environment variable on every application start.
--
-- No credential material is stored in version control by design: a committed
-- BCrypt hash is an offline-crackable secret and is rejected by the SAST gate.
INSERT INTO app_users (username, password_hash, role)
SELECT 'admin', '!', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'admin');

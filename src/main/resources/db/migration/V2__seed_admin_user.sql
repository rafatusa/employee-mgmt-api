-- Default administrator account.
-- The password hash below is a BCrypt hash of the value supplied at deploy time
-- through the ADMIN_PASSWORD environment variable; the application replaces it on
-- first start via AdminAccountInitializer when the variable is present.
INSERT INTO app_users (username, password_hash, role)
SELECT 'admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOa9NRC4E4pmb1sYVSNbUj/6E1S6dLLBu', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'admin');

-- Support guest users (unregistered / anonymous)
ALTER TABLE users ALTER COLUMN firebase_uid DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'GUEST'));

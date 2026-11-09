ALTER TABLE chat_rooms ALTER COLUMN expires_at DROP NOT NULL;
ALTER TABLE chat_rooms ALTER COLUMN default_live_time DROP NOT NULL;
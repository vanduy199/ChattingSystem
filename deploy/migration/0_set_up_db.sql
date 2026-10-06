CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(26) PRIMARY KEY, 
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    avatar TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    id VARCHAR(26) PRIMARY KEY,
    title VARCHAR(255),
    img TEXT,
    default_live_time INT,
    created_by VARCHAR(26) REFERENCES users(id) ON DELETE SET NULL,
    last_message_id VARCHAR(26), 
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS room_members (
    room_id VARCHAR(26) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id VARCHAR(26) REFERENCES users(id) ON DELETE CASCADE,
    join_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id)
);


CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(26) PRIMARY KEY,
    room_id VARCHAR(26) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id VARCHAR(26) REFERENCES users(id) ON DELETE SET NULL, 
    text TEXT,
    media TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_constraint 
        WHERE conname = 'fk_last_message'
    ) THEN
        ALTER TABLE chat_rooms
        ADD CONSTRAINT fk_last_message
        FOREIGN KEY (last_message_id) REFERENCES messages(id) ON DELETE SET NULL;
    END IF;
END;
$$;

CREATE TABLE IF NOT EXISTS message_attachments (
    id VARCHAR(26) PRIMARY KEY,
    message_id VARCHAR(26) NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    media TEXT NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_reactions (
    id VARCHAR(26) PRIMARY KEY,
    message_id VARCHAR(26) NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id VARCHAR(26) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    icon VARCHAR(36),
    UNIQUE (message_id, user_id) 
);


CREATE INDEX IF NOT EXISTS idx_messages_room_id ON messages(room_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_room_members_user_id ON room_members(user_id);

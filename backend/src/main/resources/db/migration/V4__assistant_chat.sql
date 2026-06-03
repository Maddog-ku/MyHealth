-- AI assistant: per-user avatar preference + persisted chat history.

-- Which coach persona the user wants to talk to. NULL means "follow my gender"
-- (male -> male avatar, anything else -> female avatar), resolved at read time.
ALTER TABLE profiles ADD COLUMN assistant_avatar VARCHAR(10);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL,        -- 'user' | 'assistant'
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_chat_messages_user_time ON chat_messages(user_id, created_at);

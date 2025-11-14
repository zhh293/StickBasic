CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP,
  delivered_at TIMESTAMP,
  read_at TIMESTAMP,
  recalled_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conv ON chat_message (from_user_id, to_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_to ON chat_message (to_user_id, created_at DESC);
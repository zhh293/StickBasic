CREATE TABLE IF NOT EXISTS chat_message (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            from_user_id BIGINT NOT NULL,
                                            to_user_id BIGINT NOT NULL,
                                            content TEXT NOT NULL,
                                            status VARCHAR(32) NOT NULL CHECK (status IN ('sent', 'delivered', 'read', 'recalled')),
                                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            delivered_at TIMESTAMP,
                                            read_at TIMESTAMP,
                                            recalled_at TIMESTAMP
);

-- 外键约束可以根据实际 user 表结构调整
-- ALTER TABLE chat_message ADD CONSTRAINT fk_from_user FOREIGN KEY (from_user_id) REFERENCES users(id);
-- ALTER TABLE chat_message ADD CONSTRAINT fk_to_user FOREIGN KEY (to_user_id) REFERENCES users(id);

CREATE INDEX idx_chat_msg_conv ON chat_message (from_user_id, to_user_id, created_at DESC);
CREATE INDEX idx_chat_msg_to ON chat_message (to_user_id, created_at DESC);

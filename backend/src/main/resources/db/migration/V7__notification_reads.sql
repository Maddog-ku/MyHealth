-- Tracks when each user last opened their notification center. Notifications
-- themselves are derived live (achievement unlocks + actionable reminders); only
-- this "last read" watermark is stored, to drive unread counts.

CREATE TABLE notification_reads (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  last_read_at TIMESTAMP NOT NULL DEFAULT NOW()
);

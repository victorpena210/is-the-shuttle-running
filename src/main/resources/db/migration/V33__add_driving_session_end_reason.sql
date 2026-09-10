ALTER TABLE driving_sessions
    ADD COLUMN end_reason VARCHAR(20) NULL AFTER end_method;

UPDATE driving_sessions
SET end_reason = 'COMPLETED'
WHERE ended_at IS NOT NULL
  AND end_reason IS NULL;

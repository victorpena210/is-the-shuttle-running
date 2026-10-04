-- Actual driving can occur without a planned assignment. Existing records retain their links.
ALTER TABLE driving_sessions MODIFY COLUMN assignment_id BIGINT NULL;

ALTER TABLE passenger_ride_confirmations
    MODIFY COLUMN assignment_id BIGINT NULL,
    ADD COLUMN driving_session_id BIGINT NULL,
    ADD CONSTRAINT fk_passenger_ride_session FOREIGN KEY (driving_session_id)
        REFERENCES driving_sessions (id),
    ADD CONSTRAINT uk_passenger_ride_session_device_window
        UNIQUE (driving_session_id, device_token_hash, ride_window_start);

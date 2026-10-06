ALTER TABLE driving_sessions
    ADD COLUMN last_confirmed_at DATETIME(6) NULL,
    ADD COLUMN confirmed_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_driving_session_confirmation_user FOREIGN KEY (confirmed_by_user_id) REFERENCES users(id);

ALTER TABLE operational_status_updates ADD COLUMN expires_at DATETIME(6) NULL;

ALTER TABLE organizations
    ADD COLUMN rider_pickup_instructions VARCHAR(1500) NULL,
    ADD COLUMN rider_help_contact VARCHAR(300) NULL;

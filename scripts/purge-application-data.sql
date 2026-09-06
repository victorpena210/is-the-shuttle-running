-- RoutePulse application-data purge
--
-- This permanently deletes all organizations, users,
-- invitations, tokens, drivers, assignments,
-- passenger ride confirmations, schedule imports,
-- and uploaded employee shifts.
--
-- It preserves the database schema and Flyway
-- migration history.

START TRANSACTION;

DELETE FROM passenger_ride_confirmations;
DELETE FROM uploaded_shifts;
DELETE FROM driver_assignments;
DELETE FROM schedule_imports;
DELETE FROM organization_invitations;
DELETE FROM email_verification_tokens;
DELETE FROM organization_memberships;
DELETE FROM drivers;
DELETE FROM users;
DELETE FROM organizations;

COMMIT;
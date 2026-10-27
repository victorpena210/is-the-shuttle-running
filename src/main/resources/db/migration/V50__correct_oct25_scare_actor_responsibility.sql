-- Owner-confirmed correction: Victor was responsible for SFFT scare-actor
-- transport on October 25, 2026. His driving was not coverage for Jody.
-- Match the organization, both drivers, service date, and exact service window.
-- Include any pause/resume segments for this assignment. All other windows and
-- all recorded session timestamps remain untouched. The captured driver stays
-- available in scheduled_driver_id alongside this explicit correction reason.
-- 17:00-17:45 America/Chicago on this date is 22:00-22:45 UTC.
UPDATE driving_coverage_windows w
JOIN driving_sessions s ON s.id = w.session_id
JOIN organizations o ON o.id = s.organization_id
JOIN drivers actual ON actual.id = s.driver_id AND actual.organization_id = o.id
JOIN drivers assigned ON assigned.id = w.scheduled_driver_id AND assigned.organization_id = o.id
SET w.corrected_driver_id = actual.id,
    w.assignment_correction_reason = 'Owner-confirmed assignment correction (2026-10-27): Victor was responsible for scare-actor transport on October 25; this was his own assignment, not coverage for Jody.'
WHERE o.slug = 'sfft'
  AND actual.name = 'Pena, Victor J'
  AND assigned.name = 'Irons, Jody L.'
  AND w.service_date = '2026-10-25'
  AND w.service_key = 'SCARE_ACTORS'
  AND w.starts_at = '2026-10-25 22:00:00'
  AND w.ends_at = '2026-10-25 22:45:00'
  AND w.corrected_driver_id IS NULL;

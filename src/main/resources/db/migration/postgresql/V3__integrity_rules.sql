-- PostgreSQL-only integrity guards (partial unique indexes).
-- The application enforces the same rules; these make them race-proof.

-- Business rule #7: a travel request can have many driver responses,
-- but only ONE can be accepted.
CREATE UNIQUE INDEX ux_response_one_accepted
    ON driver_response (request_id) WHERE status = 'ACCEPTED' AND deleted = FALSE;

-- A student holds at most one live travel request at a time.
CREATE UNIQUE INDEX ux_request_one_active_per_student
    ON travel_request (student_id) WHERE status IN ('OPEN', 'MATCHED', 'IN_TRIP') AND deleted = FALSE;

-- A student can be in a given companion ride only once.
CREATE UNIQUE INDEX ux_member_once_per_ride
    ON companion_ride_member (ride_id, student_id) WHERE status = 'JOINED' AND deleted = FALSE;

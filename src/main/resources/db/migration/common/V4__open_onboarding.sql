-- Enrollment numbers are no longer collected; existing values are kept.
ALTER TABLE student_profile ALTER COLUMN enrollment_no DROP NOT NULL;

-- Drivers no longer wait for approval: release everyone still in the queue.
UPDATE driver_profile SET verified = TRUE WHERE verified = FALSE;

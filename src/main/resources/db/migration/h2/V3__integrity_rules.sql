-- H2 has no partial indexes; the equivalent rules are enforced in the
-- service layer under pessimistic row locks (see MatchingService).
-- This no-op keeps the migration history identical across databases.
SELECT 1;

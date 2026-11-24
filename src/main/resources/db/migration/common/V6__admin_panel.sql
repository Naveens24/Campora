-- Admin panel: temporary passwords, last sign-in and a log of admin actions.
ALTER TABLE app_user ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

-- No foreign keys on purpose: the log outlives the accounts it mentions.
CREATE TABLE admin_action (
    id           UUID PRIMARY KEY,
    admin_id     UUID NOT NULL,
    admin_name   VARCHAR(130) NOT NULL,
    target_id    UUID,
    target_name  VARCHAR(130),
    summary      VARCHAR(160) NOT NULL,
    detail       VARCHAR(300),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    version      BIGINT  NOT NULL DEFAULT 0
);
CREATE INDEX ix_admin_action_target ON admin_action (target_id, created_at);
CREATE INDEX ix_admin_action_time   ON admin_action (created_at);

-- The audit columns used to hold phone numbers, which would survive deleting
-- an account. They hold the account id from now on; convert what is there.
UPDATE app_role SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = app_role.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE app_role SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = app_role.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE department SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = department.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE department SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = department.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE destination SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = destination.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE destination SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = destination.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE student_profile SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = student_profile.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE student_profile SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = student_profile.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE driver_profile SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = driver_profile.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE driver_profile SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = driver_profile.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE travel_request SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = travel_request.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE travel_request SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = travel_request.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE companion_ride SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = companion_ride.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE companion_ride SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = companion_ride.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE trip SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = trip.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE trip SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = trip.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE driver_response SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = driver_response.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE driver_response SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = driver_response.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE companion_ride_member SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = companion_ride_member.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE companion_ride_member SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = companion_ride_member.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE destination_request SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = destination_request.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE destination_request SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = destination_request.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE app_user SET created_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = app_user.created_by) WHERE created_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);
UPDATE app_user SET updated_by = (SELECT CAST(u.id AS VARCHAR(64)) FROM app_user u WHERE u.mobile_number = app_user.updated_by) WHERE updated_by IN (SELECT m.mobile_number FROM app_user m WHERE m.mobile_number IS NOT NULL);

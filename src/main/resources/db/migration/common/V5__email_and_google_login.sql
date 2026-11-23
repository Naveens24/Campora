-- Email codes and Google sign-in replace SMS verification.
ALTER TABLE app_user ADD COLUMN email VARCHAR(254);
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN google_subject VARCHAR(255);
ALTER TABLE app_user ADD CONSTRAINT uk_user_email UNIQUE (email);
ALTER TABLE app_user ADD CONSTRAINT uk_user_google UNIQUE (google_subject);

-- Google accounts have no password and add their mobile number afterwards.
ALTER TABLE app_user ALTER COLUMN mobile_number DROP NOT NULL;
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

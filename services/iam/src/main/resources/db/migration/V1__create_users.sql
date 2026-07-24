CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_email_trimmed_check CHECK (email = btrim(email)),
    CONSTRAINT users_email_lowercase_check CHECK (email = lower(email))
);

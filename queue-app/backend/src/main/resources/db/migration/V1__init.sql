CREATE TABLE job_postings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    unit_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_uid UUID NOT NULL UNIQUE,
    job_id BIGINT NOT NULL REFERENCES job_postings(id),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    UNIQUE (id, job_id)
);

CREATE UNIQUE INDEX ticket_sessions_one_open_job
    ON ticket_sessions (job_id)
    WHERE status = 'OPEN';

CREATE TABLE global_ticket_counter (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    current_value BIGINT NOT NULL CHECK (current_value >= 0)
);

INSERT INTO global_ticket_counter (singleton, current_value) VALUES (TRUE, 0);

CREATE TABLE draw_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_uid UUID NOT NULL UNIQUE,
    session_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL REFERENCES job_postings(id),
    phone_hmac BYTEA NOT NULL CHECK (octet_length(phone_hmac) = 32),
    phone_last4 CHAR(4) NOT NULL CHECK (phone_last4 ~ '^[0-9]{4}$'),
    hmac_key_version VARCHAR(100) NOT NULL,
    job_title_snapshot VARCHAR(200) NOT NULL,
    unit_name_snapshot VARCHAR(200) NOT NULL,
    global_number BIGINT NOT NULL UNIQUE CHECK (global_number > 0),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id, job_id) REFERENCES ticket_sessions(id, job_id),
    UNIQUE (session_id, phone_hmac)
);


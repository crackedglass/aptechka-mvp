--liquibase formatted sql
--changeset aptechka:002-tracking
CREATE TABLE tracking_patient (
    id uuid PRIMARY KEY,
    manager uuid NOT NULL UNIQUE REFERENCES app_user(id),
    caregiver uuid REFERENCES app_user(id),
    patient_login uuid REFERENCES app_user(id),
    name varchar(100) NOT NULL,
    zone varchar(100) NOT NULL
);
CREATE TABLE tracking_invitation (
    id uuid PRIMARY KEY,
    patient_id uuid NOT NULL REFERENCES tracking_patient(id),
    role varchar(12) NOT NULL,
    email varchar(254) NOT NULL,
    digest varchar(64) NOT NULL UNIQUE,
    expires timestamp with time zone NOT NULL,
    state varchar(12) NOT NULL
);
CREATE INDEX tracking_invitation_patient ON tracking_invitation(patient_id);
CREATE TABLE tracking_event (
    patient_id uuid NOT NULL REFERENCES tracking_patient(id),
    seq bigint NOT NULL,
    schema_version integer NOT NULL,
    actor uuid NOT NULL REFERENCES app_user(id),
    recorded_at timestamp with time zone NOT NULL,
    kind varchar(32) NOT NULL,
    payload text NOT NULL,
    PRIMARY KEY (patient_id, seq)
);
CREATE TABLE tracking_receipt (
    patient_id uuid NOT NULL REFERENCES tracking_patient(id),
    actor uuid NOT NULL REFERENCES app_user(id),
    request_id uuid NOT NULL,
    fingerprint text NOT NULL,
    response text NOT NULL,
    PRIMARY KEY (patient_id, actor, request_id)
);
CREATE TABLE account_access_audit (
    id uuid PRIMARY KEY,
    actor uuid NOT NULL REFERENCES app_user(id),
    recorded_at timestamp with time zone NOT NULL,
    subject uuid NOT NULL,
    kind varchar(32) NOT NULL,
    prior_state text,
    next_state text
);

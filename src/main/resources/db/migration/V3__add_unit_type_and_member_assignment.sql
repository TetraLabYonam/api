-- V3__add_unit_type_and_member_assignment.sql
ALTER TABLE place ADD COLUMN unit_type VARCHAR(20) NULL;

ALTER TABLE member ADD COLUMN location_consent_agreed_at TIMESTAMP NULL;
ALTER TABLE member ADD COLUMN assigned_place_id BIGINT NULL;
ALTER TABLE member ADD CONSTRAINT fk_member_assigned_place
    FOREIGN KEY (assigned_place_id) REFERENCES place(place_id) ON DELETE SET NULL;

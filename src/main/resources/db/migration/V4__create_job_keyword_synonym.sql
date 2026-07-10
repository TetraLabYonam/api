-- V4__create_job_keyword_synonym.sql
CREATE TABLE job_keyword_synonym (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT fk_synonym_place FOREIGN KEY (place_id) REFERENCES place(place_id) ON DELETE CASCADE
);
CREATE INDEX idx_synonym_keyword ON job_keyword_synonym(keyword);
CREATE INDEX idx_synonym_place ON job_keyword_synonym(place_id);

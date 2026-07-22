-- ============================================================================
-- Initial Schema
-- 현재 JPA 엔티티(Admin, Place, Member, Schedule, Attend, RefreshToken,
-- JobKeywordSynonym)가 실제로 기대하는 스키마와 정확히 일치하도록,
-- 실제 MariaDB에 대해 Hibernate가 생성한 DDL을 캡처해 작성했다.
-- (기존 V1~V5는 엔티티와 전혀 맞지 않아 실제로 적용된 적이 없었으므로 재작성함)
-- ============================================================================

CREATE SEQUENCE place_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775806 INCREMENT BY 50 NOCACHE NOCYCLE ENGINE=InnoDB;
CREATE SEQUENCE member_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775806 INCREMENT BY 50 NOCACHE NOCYCLE ENGINE=InnoDB;

CREATE TABLE admin (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE place (
    place_id BIGINT NOT NULL,
    unit_name VARCHAR(255),
    place_address VARCHAR(255),
    latitude DOUBLE,
    longitude DOUBLE,
    image_url VARCHAR(255),
    phone_number VARCHAR(255),
    description VARCHAR(1000),
    unit_type ENUM('MARKET','PUBLIC_INTEREST','SOCIAL_SERVICE'),
    PRIMARY KEY (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE member (
    member_id BIGINT NOT NULL,
    username VARCHAR(255),
    guardian_phone VARCHAR(255),
    unit_name VARCHAR(255),
    unit_type VARCHAR(255),
    location_consent_agreed_at DATETIME(6),
    assigned_place_id BIGINT,
    employee_id BIGINT,
    phone_number_hash VARCHAR(255),
    active BIT(1) NOT NULL,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE schedule (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    schedule_date DATE NOT NULL,
    start_time TIME(6),
    end_time TIME(6),
    place_id BIGINT NOT NULL,
    created_by BIGINT,
    is_active BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (schedule_id),
    KEY idx_schedule_place (place_id),
    KEY idx_schedule_created_by (created_by),
    CONSTRAINT fk_schedule_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    CONSTRAINT fk_schedule_admin FOREIGN KEY (created_by) REFERENCES admin (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attend (
    attend_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    status ENUM('ABSENT','EXCUSED','LATE','PRESENT','SCHEDULED') NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    attended_at DATETIME(6),
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attend_id),
    KEY idx_attend_member (member_id),
    KEY idx_attend_schedule (schedule_id),
    CONSTRAINT fk_attend_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_attend_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    device_id VARCHAR(255),
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE job_keyword_synonym (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_synonym_place (place_id),
    CONSTRAINT fk_synonym_place FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_synonym_keyword ON job_keyword_synonym(keyword);

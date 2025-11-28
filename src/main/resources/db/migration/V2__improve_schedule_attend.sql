-- ============================================================================
-- Schedule & Attendance System Improvement Migration
-- Version: 2.0
-- Date: 2025-11-25
-- Description: Schedule 및 Attend 테이블 개선
-- ============================================================================

-- ============================================================================
-- 1. SCHEDULE 테이블 개선
-- ============================================================================

-- 기존 컬럼 타입 확인 및 변경
-- AttendDate → schedule_date로 변경 (Date → DATE)
ALTER TABLE SCHEDULE
    CHANGE COLUMN AttendDate schedule_date DATE NOT NULL;

-- 새로운 컬럼 추가
ALTER TABLE SCHEDULE
    ADD COLUMN IF NOT EXISTS title VARCHAR(200) NOT NULL DEFAULT '미지정 일정',
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time TIME,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Admin FK 추가 (Admin 테이블이 이미 존재하는 경우)
-- Foreign Key 이름이 중복되지 않도록 체크
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'SCHEDULE'
    AND CONSTRAINT_NAME = 'fk_schedule_admin'
);

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE SCHEDULE ADD CONSTRAINT fk_schedule_admin FOREIGN KEY (created_by) REFERENCES ADMIN(id) ON DELETE SET NULL',
    'SELECT "FK already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 기존 Schedule 데이터에 기본 제목 설정 (title이 '미지정 일정'인 경우)
UPDATE SCHEDULE
SET title = CONCAT('일정 ', DATE_FORMAT(schedule_date, '%Y-%m-%d'))
WHERE title = '미지정 일정';

-- ============================================================================
-- 2. ATTEND 테이블 개선
-- ============================================================================

-- 새로운 컬럼 추가
ALTER TABLE ATTEND
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN IF NOT EXISTS attended_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 기존 컬럼명 변경 (일관성을 위해)
-- PLACE_LATITUDE, PLACE_LONGITUDE → latitude, longitude
ALTER TABLE ATTEND
    CHANGE COLUMN IF EXISTS PLACE_LATITUDE latitude DOUBLE,
    CHANGE COLUMN IF EXISTS PLACE_LONGITUDE longitude DOUBLE;

-- 기존 Attend 데이터의 상태 설정
-- latitude/longitude가 있으면 PRESENT, 없으면 SCHEDULED
UPDATE ATTEND
SET status = CASE
    WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 'PRESENT'
    ELSE 'SCHEDULED'
END
WHERE status = 'SCHEDULED';

-- AttendStatus CHECK 제약조건 추가
ALTER TABLE ATTEND
    ADD CONSTRAINT IF NOT EXISTS chk_attend_status
    CHECK (status IN ('SCHEDULED', 'PRESENT', 'ABSENT', 'LATE', 'EXCUSED'));

-- ============================================================================
-- 3. 인덱스 추가 (성능 최적화)
-- ============================================================================

-- SCHEDULE 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_schedule_date ON SCHEDULE(schedule_date);
CREATE INDEX IF NOT EXISTS idx_schedule_place ON SCHEDULE(place_id);
CREATE INDEX IF NOT EXISTS idx_schedule_active ON SCHEDULE(is_active);
CREATE INDEX IF NOT EXISTS idx_schedule_created_by ON SCHEDULE(created_by);

-- ATTEND 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_attend_member ON ATTEND(member_id);
CREATE INDEX IF NOT EXISTS idx_attend_schedule ON ATTEND(schedule_id);
CREATE INDEX IF NOT EXISTS idx_attend_status ON ATTEND(status);
CREATE INDEX IF NOT EXISTS idx_attend_member_schedule ON ATTEND(member_id, schedule_id);

-- ============================================================================
-- 4. 데이터 무결성 검증
-- ============================================================================

-- Schedule 테이블의 place_id가 NULL인 경우 확인
SELECT COUNT(*) AS invalid_schedules
FROM SCHEDULE
WHERE place_id IS NULL;

-- Attend 테이블의 member_id 또는 schedule_id가 NULL인 경우 확인
SELECT COUNT(*) AS invalid_attends
FROM ATTEND
WHERE member_id IS NULL OR schedule_id IS NULL;

-- ============================================================================
-- 5. 통계 정보 업데이트
-- ============================================================================

-- 테이블 분석 (옵티마이저 힌트용)
ANALYZE TABLE SCHEDULE;
ANALYZE TABLE ATTEND;
ANALYZE TABLE MEMBER;
ANALYZE TABLE PLACE;

-- ============================================================================
-- Migration 완료 로그
-- ============================================================================
-- 마이그레이션 완료 시간 기록
-- (Flyway가 자동으로 기록하므로 별도 작업 불필요)

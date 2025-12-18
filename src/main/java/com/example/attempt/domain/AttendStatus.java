package com.example.attempt.domain;

/**
 * 출석 상태를 나타내는 Enum
 */
public enum AttendStatus {
    /**
     * 출석 예정 (일정 생성 시 기본값)
     */
    SCHEDULED("출석 예정"),

    /**
     * 출석 완료
     */
    PRESENT("출석"),

    /**
     * 결석
     */
    ABSENT("결석"),

    /**
     * 지각
     */
    LATE("지각"),

    /**
     * 사유 인정 결석
     */
    EXCUSED("사유 인정");

    private final String description;

    AttendStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 실제 출석 처리가 완료된 상태인지 확인
     */
    public boolean isAttended() {
        return this == PRESENT || this == LATE;
    }

    /**
     * 결석 상태인지 확인
     */
    public boolean isAbsent() {
        return this == ABSENT || this == EXCUSED;
    }
}
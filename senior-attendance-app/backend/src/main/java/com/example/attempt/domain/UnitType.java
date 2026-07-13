package com.example.attempt.domain;

/**
 * 사업단 유형
 */
public enum UnitType {
    PUBLIC_INTEREST("공익형"),
    MARKET("시장형"),
    SOCIAL_SERVICE("사회서비스형");

    private final String description;

    UnitType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 값 타입 복사 - 엔티티로 만들면 관리 가능

@Embeddable
@Getter
@NoArgsConstructor
public class Unit {

    @Column(name = "UNIT_NAME")
    private String unitName;

    @Column(name = "UNIT_TYPE")
    private String unitType;

    public Unit(String unitName) {
        this.unitName = unitName;
    }
}


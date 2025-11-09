package com.example.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Unit {

    @Id @GeneratedValue
    @Column(name = "UNIT_ID")
    private Long id;

    @Column(name = "UNIT_NAME")
    private String name;

    @Column(name = "UNIT_TYPE")
    private String type;

    public Unit(String name) {
        this.name = name;
    }

}

package com.example.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Unit {

    @Id @GeneratedValue
    @Column(name = "UNIT_ID")
    private Long id;

    @Column(name = "UNIT_NAME")
    private String name;
}

package com.example.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class Place {
    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "place_name")
    private String name;

    private Double latitude;
    private Double longitude;

    public Place(String name, Double latitude, Double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

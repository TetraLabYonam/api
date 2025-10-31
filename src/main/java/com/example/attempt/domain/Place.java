package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
public class Place {
    @Id
    @GeneratedValue
    @Column(name = "PLACE_ID")
    private Long id;

    @Column(name = "place_name")
    private String name;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "place")
    private List<Schedule>  schedules = new ArrayList<>();

    private Double latitude;
    private Double longitude;

    public Place(String name, Double latitude, Double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Data
    @AllArgsConstructor
    class LocationDto {
        private double lat;
        private double lng;
    }
}

package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Data
public class Place {
    @Id
    @GeneratedValue
    @Column(name = "PLACE_ID")
    private Long id;

    @Column(name = "unit_name")
    private String name;

    @Column(name = "place_address")
    private String address;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "place")
    private List<Schedule> schedules = new ArrayList<>();

    private Double latitude;
    private Double longitude;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20)
    private UnitType unitType;


    public Place(String name, String address, Double latitude, Double longitude) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Place(String name, String address, Double latitude, Double longitude, String imageUrl) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
    }

    public Place(String name, String address, Double latitude, Double longitude, String imageUrl, String phoneNumber, String description) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.phoneNumber = phoneNumber;
        this.description = description;
    }

}

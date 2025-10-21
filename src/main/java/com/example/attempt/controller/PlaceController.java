package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PlaceController {

    @Value("${geocoding-api-key}")
    private String AUTH_TOKEN;

    @GetMapping("/api/v1/place")
    public LocationDto getPlace() throws IOException, InterruptedException, ApiException {

        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(AUTH_TOKEN)
                .build();
        GeocodingResult[] response =  GeocodingApi.geocode(context,
                "경상남도 양산시 남부13길 10, 50622").await();
// Invoke .shutdown() after your application is done making requests
        context.shutdown();

        return new LocationDto(
                response[0].geometry.location.lat,
                response[0].geometry.location.lng
        );
    }

    @Data
    @AllArgsConstructor
    class LocationDto {
        private double lat;
        private double lng;
    }
}

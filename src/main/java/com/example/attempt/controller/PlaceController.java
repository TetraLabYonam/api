package com.example.attempt.controller;

import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.domain.Place;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class PlaceController {
    private final PlaceRepository placeRepository;

    public PlaceController(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @PostMapping("/api/place/save")
    public ResponseEntity<String> savePlace(@RequestBody LocationDto locationDto) {
        try {
            Place place = new Place(
                    locationDto.getBusiness_unit(),
                    locationDto.getAddress(),
                    locationDto.getLat(),
                    locationDto.getLng(),
                    null, // imageUrl
                    locationDto.getPhone_number(),
                    locationDto.getDescription()
            );
            placeRepository.save(place);
            return ResponseEntity.ok("장소가 성공적으로 저장되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("장소 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/api/place/save-all")
    public ResponseEntity<String> savePlaces(@RequestBody List<LocationDto> locationDtos) {
        try {
            List<Place> places = new ArrayList<>();
            for (LocationDto dto : locationDtos) {
                Place place = new Place(
                        dto.getBusiness_unit(),
                        dto.getAddress(),
                        dto.getLat(),
                        dto.getLng(),
                        null, // imageUrl
                        dto.getPhone_number(),
                        dto.getDescription()
                );
                places.add(place);
            }
            placeRepository.saveAll(places);
            return ResponseEntity.ok(places.size() + "개의 장소가 성공적으로 저장되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("장소 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @GetMapping("/api/place")
    public ResponseEntity<List<AddressDto>> getPlace() {
        try {
            List<Place> places = placeRepository.findAll();
            List<AddressDto> results = new ArrayList<>();

            for (Place place : places) {
                results.add(new AddressDto(
                    place.getImageUrl() != null ? place.getImageUrl() : "",
                    place.getName() != null ? place.getName() : ""
                ));
            }

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    @GetMapping("/api/place/list")
    public ResponseEntity<List<LocationDto>> getPlaceList() {
        try {
            List<Place> places = placeRepository.findAll();
            List<LocationDto> results = new ArrayList<>();

            for (Place place : places) {
                results.add(new LocationDto(
                    place.getName() != null ? place.getName() : "",
                    place.getAddress() != null ? place.getAddress() : "",
                    place.getLatitude(),
                    place.getLongitude(),
                    place.getPhoneNumber(),
                    place.getDescription()
                ));
            }

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }



    // LocationDto: Flutter JobPlace 모델과 매핑
    public static class LocationDto {
        private String business_unit;  // 사업단명
        private String address;
        private Double lat;
        private Double lng;
        private String phone_number;
        private String description;

        public LocationDto() {}

        public LocationDto(String business_unit, String address, Double lat, Double lng) {
            this.business_unit = business_unit;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
        }

        public LocationDto(String business_unit, String address, Double lat, Double lng, String phone_number, String description) {
            this.business_unit = business_unit;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.phone_number = phone_number;
            this.description = description;
        }

        public String getBusiness_unit() {
            return business_unit;
        }

        public void setBusiness_unit(String business_unit) {
            this.business_unit = business_unit;
        }

        // Deprecated: 하위 호환성을 위해 유지
        public String getBusinessUnit() {
            return business_unit;
        }

        public void setBusinessUnit(String businessUnit) {
            this.business_unit = businessUnit;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }

        public Double getLng() {
            return lng;
        }

        public void setLng(Double lng) {
            this.lng = lng;
        }

        public String getPhone_number() {
            return phone_number;
        }

        public void setPhone_number(String phone_number) {
            this.phone_number = phone_number;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // 요청한 JSON 키와 동일하게 맞춤
    public static class AddressDto {
        public String image_url;
        public String text;

        public AddressDto(String image_url, String text) {
            this.image_url = image_url;
            this.text = text;
        }

        public AddressDto() {}
    }
}




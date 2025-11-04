package com.example.attempt.controller;

import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.domain.Place;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
public class PlaceController {

    private final PlaceRepository placeRepository;

    public PlaceController(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    private static final List<String> BASE_URLS = List.of(
            "http://www.yscsc.co.kr/02/01.php",  // 공익 활동사업
            "http://www.yscsc.co.kr/02/02.php",  // 공동체사업단
            "http://www.yscsc.co.kr/02/03.php"   // 노인역량활용사업
    );


    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();

    @Value("${geocoding-api-key}")
    private String AUTH_TOKEN;

    @PostMapping("/api/place/save")
    public ResponseEntity<String> savePlace(@RequestBody LocationDto locationDto) {
        try {
            Place place = new Place(
                    locationDto.getBusinessUnit(),
                    locationDto.getAddress(),
                    locationDto.getLat(),
                    locationDto.getLng()
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
                        dto.getBusinessUnit(),
                        dto.getAddress(),
                        dto.getLat(),
                        dto.getLng()
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
        List<AddressDto> results = new ArrayList<>();

        for (String baseUrl : BASE_URLS) {
            try {
                // baseUrl로 요청. Jsoup은 문서의 base URI를 유지하므로 attr("abs:src") 사용 가능
                Document doc = Jsoup
                        .connect(baseUrl)
                        .userAgent("Mozilla/5.0")
                        .referrer("https://www.google.com")
                        .timeout(TIMEOUT_MS)
                        .get();

                Elements activityBoxes = doc.select("div.sub02_01_in_box");

                for (Element box : activityBoxes) {
                    // 이미지
                    Element imgTag = box.selectFirst("img");
                    String fullImgUrl = imgTag != null ? imgTag.attr("abs:src") : "";

                    // 제목
                    Element titleTag = box.selectFirst("p.sub02_01_in_box_right_title");
                    String titleText = titleTag != null ? titleTag.text().trim() : "";

                    results.add(new AddressDto(fullImgUrl, titleText));
                }
            } catch (IOException e) {
                // 부분 실패는 로그로 처리하고 계속 진행하고 싶다면 그대로 둠
                // 전부 실패 시 502 등으로 반환하려면 누적하여 판단
                // 여기서는 부분 실패 허용
            }
        }

        return results.isEmpty()
                ? ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(results)
                : ResponseEntity.ok(results);
    }



    // LocationDto: MapController의 LocationDto와 동일한 구조
    public static class LocationDto {
        private String businessUnit;  // 사업단명
        private String address;
        private Double lat;
        private Double lng;

        public LocationDto() {}

        public LocationDto(String businessUnit, String address, Double lat, Double lng) {
            this.businessUnit = businessUnit;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
        }

        public String getBusinessUnit() {
            return businessUnit;
        }

        public void setBusinessUnit(String businessUnit) {
            this.businessUnit = businessUnit;
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




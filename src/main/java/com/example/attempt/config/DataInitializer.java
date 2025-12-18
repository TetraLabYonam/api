package com.example.attempt.config;

import com.example.attempt.domain.Place;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.service.PlaceCrawlingService;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PlaceRepository placeRepository;
    private final PlaceCrawlingService placeCrawlingService;

    @Value("${geocoding-api-key:}")
    private String geocodingApiKey;

    @Override
    public void run(String... args) throws Exception {
        // 초기 데이터를 삽입하지 않고 테이블을 비워둡니다.
        initializePlacesFromCrawling();
    }

    /**
     * 웹 크롤링을 통해 Place 데이터를 초기화합니다.
     */
    private void initializePlacesFromCrawling() {
        log.info("웹 크롤링을 통한 Place 데이터 초기화 시작");

        try {
            // 웹 크롤링으로 데이터 수집
            List<PlaceCrawlingService.PlaceData> crawledData = placeCrawlingService.crawlPlaceData();

            if (crawledData.isEmpty()) {
                log.warn("크롤링된 데이터가 없습니다. 기본 데이터를 삽입합니다.");
                initializeDefaultPlaces();
                return;
            }

            // API 키가 없으면 Geocoding 없이 저장
            if (geocodingApiKey == null || geocodingApiKey.trim().isEmpty()) {
                log.warn("Geocoding API 키가 설정되지 않았습니다. 위도/경도 없이 데이터를 저장합니다.");
                int savedCount = 0;
                for (PlaceCrawlingService.PlaceData data : crawledData) {
                    try {
                        Place place = new Place(
                                data.getTitle() != null ? data.getTitle() : "정보 없음",
                                data.getAddress() != null && !data.getAddress().isEmpty() ? data.getAddress() : "주소 정보 없음",
                                null,
                                null,
                                data.getImageUrl(),
                                data.getPhoneNumber(),
                                data.getDescription()
                        );
                        placeRepository.save(place);
                        savedCount++;
                        log.info("Place 저장 완료 (Geocoding 없이): {}", place.getName());
                    } catch (Exception e) {
                        log.error("Place 저장 실패: {} - {}", data.getTitle(), e.getMessage());
                    }
                }
                log.info("총 {}개의 Place 데이터가 초기화되었습니다 (Geocoding 없이).", savedCount);
                return;
            }

            // GeoApiContext 생성
            GeoApiContext geoContext = new GeoApiContext.Builder()
                    .apiKey(geocodingApiKey)
                    .build();

            int savedCount = 0;
            try {
                for (PlaceCrawlingService.PlaceData data : crawledData) {
                    try {
                        Place place = convertToPlace(data, geoContext);
                        if (place != null) {
                            placeRepository.save(place);
                            savedCount++;
                            log.info("Place 저장 완료: {}", place.getName());
                        }

                        // API 호출 제한을 피하기 위한 딜레이
                        Thread.sleep(100);
                    } catch (Exception e) {
                        log.error("Place 저장 실패: {} - {}", data.getTitle(), e.getMessage());
                    }
                }
            } finally {
                geoContext.shutdown();
            }

            log.info("총 {}개의 Place 데이터가 초기화되었습니다.", savedCount);

        } catch (Exception e) {
            log.error("크롤링 중 오류 발생: {}", e.getMessage(), e);
            log.info("기본 데이터를 삽입합니다.");
            initializeDefaultPlaces();
        }
    }

    /**
     * PlaceData를 Place 엔티티로 변환합니다.
     * 주소가 있으면 Geocoding으로 위도/경도를 가져옵니다.
     */
    private Place convertToPlace(PlaceCrawlingService.PlaceData data, GeoApiContext geoContext) {
        Double latitude = null;
        Double longitude = null;

        // 주소가 있으면 Geocoding 시도
        if (data.getAddress() != null && !data.getAddress().isEmpty()) {
            try {
                GeocodingResult[] results = GeocodingApi.geocode(geoContext, data.getAddress()).await();
                if (results != null && results.length > 0) {
                    latitude = results[0].geometry.location.lat;
                    longitude = results[0].geometry.location.lng;
                    log.info("Geocoding 성공: {} -> ({}, {})", data.getAddress(), latitude, longitude);
                }
            } catch (Exception e) {
                log.warn("Geocoding 실패: {} - {}", data.getAddress(), e.getMessage());
            }
        }

        // 주소가 없거나 Geocoding 실패 시 제목으로 시도
        if (latitude == null && data.getTitle() != null && !data.getTitle().isEmpty()) {
            try {
                GeocodingResult[] results = GeocodingApi.geocode(geoContext, data.getTitle()).await();
                if (results != null && results.length > 0) {
                    latitude = results[0].geometry.location.lat;
                    longitude = results[0].geometry.location.lng;
                    log.info("제목으로 Geocoding 성공: {} -> ({}, {})", data.getTitle(), latitude, longitude);
                }
            } catch (Exception e) {
                log.warn("제목으로 Geocoding 실패: {} - {}", data.getTitle(), e.getMessage());
            }
        }

        // Place 엔티티 생성 (위도/경도는 null일 수 있음)
        return new Place(
                data.getTitle() != null ? data.getTitle() : "정보 없음",
                data.getAddress() != null && !data.getAddress().isEmpty() ? data.getAddress() : "주소 정보 없음",
                latitude,
                longitude,
                data.getImageUrl(),
                data.getPhoneNumber(),
                data.getDescription()
        );
    }

    /**
     * 크롤링 실패 시 사용할 기본 데이터를 삽입합니다.
     */
    private void initializeDefaultPlaces() {
        Place place1 = new Place(
                "서울중앙우체국",
                "서울특별시 종로구 종로 6",
                37.5703,
                126.9772,
                "/images/places/seoul-post.jpg",
                "02-6450-1114",
                "서울 종로구에 위치한 중앙우체국입니다."
        );

        Place place2 = new Place(
                "부산역",
                "부산광역시 동구 중앙대로 206",
                35.1149,
                129.0419,
                "/images/places/busan-station.jpg",
                "1544-7788",
                "부산의 주요 기차역입니다."
        );

        Place place3 = new Place(
                "제주공항",
                "제주특별자치도 제주시 공항로 2",
                33.5113,
                126.4931,
                "/images/places/jeju-airport.jpg",
                "064-797-2114",
                "제주도의 국제공항입니다."
        );

        Place place4 = new Place(
                "광화문광장",
                "서울특별시 종로구 세종로",
                37.5759,
                126.9768,
                "/images/places/gwanghwamun.jpg",
                "02-2133-7744",
                "서울의 대표적인 광장입니다."
        );

        Place place5 = new Place(
                "대구타워",
                "대구광역시 중구 달구벌대로 2077",
                35.8528,
                128.5634,
                "/images/places/daegu-tower.jpg",
                "053-621-5500",
                "대구를 대표하는 관광명소입니다."
        );

        placeRepository.save(place1);
        placeRepository.save(place2);
        placeRepository.save(place3);
        placeRepository.save(place4);
        placeRepository.save(place5);

        log.info("기본 Place 데이터 5개가 성공적으로 삽입되었습니다.");
    }
}
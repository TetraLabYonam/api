package com.example.attempt.service;

import com.example.attempt.domain.Place;
import com.example.attempt.dto.AddressDto;
import com.example.attempt.dto.PlaceDto;
import com.example.attempt.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 일자리(장소) 관련 비즈니스 로직 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;

    /**
     * 단일 장소 저장
     * @param placeDto 저장할 장소 정보
     * @return 저장된 장소 엔티티
     */
    @Transactional
    public Place savePlace(PlaceDto placeDto) {
        Place place = new Place(
                placeDto.getBusiness_unit(),
                placeDto.getAddress(),
                placeDto.getLat(),
                placeDto.getLng(),
                null,  // imageUrl
                placeDto.getPhone_number(),
                placeDto.getDescription()
        );

        Place savedPlace = placeRepository.save(place);
        log.info("장소 저장 완료: {}", savedPlace.getName());
        return savedPlace;
    }

    /**
     * 여러 장소 일괄 저장 (기존 데이터 삭제 후)
     * @param placeDtoList 저장할 장소 목록
     * @return 저장된 장소 개수
     */
    @Transactional
    public int savePlaces(List<PlaceDto> placeDtoList) {
        // 기존 데이터 모두 삭제
        placeRepository.deleteAll();
        log.info("기존 장소 데이터 삭제 완료");

        List<Place> places = placeDtoList.stream()
                .map(dto -> new Place(
                        dto.getBusiness_unit(),
                        dto.getAddress(),
                        dto.getLat(),
                        dto.getLng(),
                        null,  // imageUrl
                        dto.getPhone_number(),
                        dto.getDescription()
                ))
                .collect(Collectors.toList());

        List<Place> savedPlaces = placeRepository.saveAll(places);
        log.info("{}개의 장소 저장 완료", savedPlaces.size());
        return savedPlaces.size();
    }

    /**
     * 간단한 주소 목록 조회 (이미지 URL, 이름만)
     * @return 주소 DTO 목록
     */
    @Transactional(readOnly = true)
    public List<AddressDto> getPlaces() {
        List<Place> places = placeRepository.findAll();
        return places.stream()
                .map(place -> new AddressDto(
                        place.getImageUrl() != null ? place.getImageUrl() : "",
                        place.getName() != null ? place.getName() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 상세 장소 목록 조회
     * @return 장소 DTO 목록
     */
    @Transactional(readOnly = true)
    public List<PlaceDto> getPlaceList() {
        List<Place> places = placeRepository.findAll();
        return places.stream()
                .map(place -> new PlaceDto(
                        place.getName() != null ? place.getName() : "",
                        place.getAddress() != null ? place.getAddress() : "",
                        place.getLatitude(),
                        place.getLongitude(),
                        place.getPhoneNumber(),
                        place.getDescription()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 모든 장소 조회
     * @return 장소 엔티티 목록
     */
    @Transactional(readOnly = true)
    public List<Place> getAllPlaces() {
        return placeRepository.findAll();
    }
}
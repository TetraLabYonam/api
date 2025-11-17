package com.example.attempt.controller;

import com.example.attempt.dto.AddressDto;
import com.example.attempt.dto.PlaceDto;
import com.example.attempt.service.PlaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 일자리(장소) 관련 REST API 컨트롤러
 * Flutter 앱과 통신하는 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/place")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    /**
     * 단일 장소 저장
     * POST /api/place/save
     *
     * @param placeDto 저장할 장소 정보
     * @return 성공 메시지
     */
    @PostMapping("/save")
    public ResponseEntity<String> savePlace(@RequestBody PlaceDto placeDto) {
        log.info("장소 저장 요청 - name: {}", placeDto.getBusiness_unit());
        placeService.savePlace(placeDto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("장소가 성공적으로 저장되었습니다.");
    }

    /**
     * 여러 장소 일괄 저장
     * POST /api/place/save-all
     *
     * @param placeDtoList 저장할 장소 목록
     * @return 성공 메시지
     */
    @PostMapping("/save-all")
    public ResponseEntity<String> savePlaces(@RequestBody List<PlaceDto> placeDtoList) {
        log.info("장소 일괄 저장 요청 - count: {}", placeDtoList.size());
        int savedCount = placeService.savePlaces(placeDtoList);
        return ResponseEntity.ok(savedCount + "개의 장소가 성공적으로 저장되었습니다.");
    }

    /**
     * 장소 목록 조회 (간단)
     * GET /api/place
     *
     * @return 주소 DTO 목록 (이미지 URL, 이름만)
     */
    @GetMapping
    public ResponseEntity<List<AddressDto>> getPlace() {
        log.info("장소 목록 조회 요청 (간단)");
        List<AddressDto> places = placeService.getPlaces();
        return ResponseEntity.ok(places);
    }

    /**
     * 장소 목록 조회 (상세)
     * GET /api/place/list
     *
     * @return 장소 DTO 목록 (전체 정보)
     */
    @GetMapping("/list")
    public ResponseEntity<List<PlaceDto>> getPlaceList() {
        log.info("장소 목록 조회 요청 (상세)");
        List<PlaceDto> places = placeService.getPlaceList();
        return ResponseEntity.ok(places);
    }
}




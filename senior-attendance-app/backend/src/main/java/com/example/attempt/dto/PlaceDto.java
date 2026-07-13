package com.example.attempt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일자리(장소) 정보 전송 객체
 * Flutter JobPlace 모델과 매핑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDto {
    private String business_unit;  // 사업단명
    private String address;        // 주소
    private Double lat;            // 위도
    private Double lng;            // 경도
    private String phone_number;   // 전화번호
    private String description;    // 설명

    public PlaceDto(String business_unit, String address, Double lat, Double lng) {
        this.business_unit = business_unit;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    // 하위 호환성을 위한 메서드
    public String getBusinessUnit() {
        return business_unit;
    }

    public void setBusinessUnit(String businessUnit) {
        this.business_unit = businessUnit;
    }
}
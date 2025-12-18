package com.example.attempt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주소 정보 간단 전송 객체
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
    private String image_url;  // 이미지 URL
    private String text;       // 텍스트 (장소명)
}
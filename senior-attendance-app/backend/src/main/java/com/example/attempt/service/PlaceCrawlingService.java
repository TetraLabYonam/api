package com.example.attempt.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PlaceCrawlingService {

    // 크롤링할 대상 URL 목록
    private static final String[] BASE_URLS = {
            "http://www.yscsc.co.kr/02/01.php",  // 공익 활동사업
            "http://www.yscsc.co.kr/02/02.php",  // 공동체사업단
            "http://www.yscsc.co.kr/02/03.php"   // 노인역량활용사업
    };

    /**
     * 웹 사이트에서 Place 데이터를 크롤링합니다.
     * @return 크롤링된 Place 데이터 리스트
     */
    public List<PlaceData> crawlPlaceData() {
        List<PlaceData> results = new ArrayList<>();

        for (String baseUrl : BASE_URLS) {
            try {
                log.info("크롤링 시작: {}", baseUrl);

                // 웹 페이지 가져오기
                Document document = Jsoup.connect(baseUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();

                // div.sub02_01_in_box 요소들 찾기
                Elements activityBoxes = document.select("div.sub02_01_in_box");

                for (Element box : activityBoxes) {
                    PlaceData placeData = new PlaceData();

                    // 이미지 URL 추출
                    Element imgTag = box.selectFirst("img");
                    if (imgTag != null) {
                        String imgSrc = imgTag.attr("src");
                        // 상대 경로를 절대 경로로 변환
                        String fullImgUrl = imgSrc.startsWith("http")
                                ? imgSrc
                                : "http://www.yscsc.co.kr" + (imgSrc.startsWith("/") ? imgSrc : "/" + imgSrc);
                        placeData.setImageUrl(fullImgUrl);
                    } else {
                        placeData.setImageUrl("");
                    }

                    // 제목 추출
                    Element titleTag = box.selectFirst("p.sub02_01_in_box_right_title");
                    if (titleTag != null) {
                        placeData.setTitle(titleTag.text().trim());
                    } else {
                        placeData.setTitle("");
                    }

                    // 주소 추출 시도 (여러 가능한 선택자 시도)
                    String address = extractAddress(box);
                    placeData.setAddress(address);

                    // 전화번호 추출 시도
                    String phoneNumber = extractPhoneNumber(box);
                    placeData.setPhoneNumber(phoneNumber);

                    // 설명 추출 시도
                    String description = extractDescription(box);
                    placeData.setDescription(description);

                    log.info("크롤링 완료 - 제목: {}, 이미지: {}, 주소: {}",
                            placeData.getTitle(), placeData.getImageUrl(), placeData.getAddress());

                    results.add(placeData);
                }

                // API 호출 제한을 피하기 위한 딜레이
                Thread.sleep(100);

            } catch (IOException e) {
                log.error("크롤링 실패: {} - {}", baseUrl, e.getMessage());
            } catch (InterruptedException e) {
                log.error("크롤링 중 인터럽트 발생: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        log.info("전체 크롤링 완료. 총 {}개의 데이터 수집", results.size());
        return results;
    }

    /**
     * 주소 정보 추출 (여러 선택자 시도)
     */
    private String extractAddress(Element box) {
        // 다양한 선택자로 주소 추출 시도
        String[] addressSelectors = {
                "p.sub02_01_in_box_right_address",
                "p.address",
                "span.address",
                "div.address"
        };

        for (String selector : addressSelectors) {
            Element addressTag = box.selectFirst(selector);
            if (addressTag != null && !addressTag.text().trim().isEmpty()) {
                return addressTag.text().trim();
            }
        }

        // 텍스트에서 주소 패턴 찾기 (예: "주소:" 또는 "위치:" 다음에 오는 텍스트)
        String fullText = box.text();
        if (fullText.contains("주소:") || fullText.contains("주소 :")) {
            int startIdx = fullText.contains("주소:") ? fullText.indexOf("주소:") + 3 : fullText.indexOf("주소 :") + 4;
            String remainingText = fullText.substring(startIdx).trim();
            // 다음 필드가 나올 때까지 또는 줄바꿈까지
            int endIdx = Math.min(
                    remainingText.indexOf("전화") != -1 ? remainingText.indexOf("전화") : remainingText.length(),
                    remainingText.indexOf("TEL") != -1 ? remainingText.indexOf("TEL") : remainingText.length()
            );
            return remainingText.substring(0, endIdx).trim();
        }

        // 주소를 찾지 못한 경우 빈 문자열 반환
        return "";
    }

    /**
     * 전화번호 정보 추출
     */
    private String extractPhoneNumber(Element box) {
        // 다양한 선택자로 전화번호 추출 시도
        String[] phoneSelectors = {
                "p.phone",
                "span.phone",
                "p.tel"
        };

        for (String selector : phoneSelectors) {
            Element phoneTag = box.selectFirst(selector);
            if (phoneTag != null && !phoneTag.text().trim().isEmpty()) {
                return phoneTag.text().trim();
            }
        }

        // 텍스트에서 전화번호 패턴 찾기
        String fullText = box.text();
        if (fullText.contains("전화:") || fullText.contains("TEL:") || fullText.contains("Tel:")) {
            String marker = fullText.contains("전화:") ? "전화:" :
                           fullText.contains("TEL:") ? "TEL:" : "Tel:";
            int startIdx = fullText.indexOf(marker) + marker.length();
            String remainingText = fullText.substring(startIdx).trim();
            // 전화번호 패턴 추출 (숫자와 하이픈)
            String[] parts = remainingText.split("\\s+");
            if (parts.length > 0) {
                return parts[0].replaceAll("[^0-9-]", "");
            }
        }

        return "";
    }

    /**
     * 설명 정보 추출
     */
    private String extractDescription(Element box) {
        // 다양한 선택자로 설명 추출 시도
        String[] descriptionSelectors = {
                "p.sub02_01_in_box_right_desc",
                "p.description",
                "div.description"
        };

        for (String selector : descriptionSelectors) {
            Element descTag = box.selectFirst(selector);
            if (descTag != null && !descTag.text().trim().isEmpty()) {
                return descTag.text().trim();
            }
        }

        return "";
    }

    /**
     * 크롤링된 Place 데이터를 담는 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceData {
        private String imageUrl;
        private String title;
        private String address;
        private String phoneNumber;
        private String description;
    }
}

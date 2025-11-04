package com.example.attempt.controller;

import com.example.attempt.service.ExcelService;
import com.example.attempt.service.ExcelService.ExcelData;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Controller
@RequiredArgsConstructor
public class MapController {

    private final ExcelService excelService;

    @Value("${geocoding-api-key}")
    private String AUTH_TOKEN;

    private final String ADDRESS ="경상남도 양산시 남부13길 10, 50622";

    @GetMapping("/mapV1")
    public String showMapV1(Model model) throws IOException, InterruptedException, ApiException {
        List<LocationDto> locations = getLocations();

        // 첫 번째 위치 정보를 개별 속성으로 전달
        if (!locations.isEmpty()) {
            LocationDto location = locations.get(0);
            model.addAttribute("lat", location.getLat());
            model.addAttribute("lng", location.getLng());
            model.addAttribute("address", ADDRESS);
        }

        model.addAttribute("googleMapsApiKey", AUTH_TOKEN);
        return "map";
    }

    @GetMapping("/map-excel")
    public String showMapXls(Model model) {
        model.addAttribute("googleMapsApiKey", AUTH_TOKEN);
        return "excel";
    }

    @GetMapping("/member")
    public String showMember() {
        return "member";
    }

    @GetMapping("/schedule")
    public String showSchedule() {
        return "schedule";
    }

    @PostMapping("/map-excel")
    public String showMapXls(@RequestParam("file") MultipartFile file, Model model) {
        try {
            // 엑셀 파일에서 사업단명과 주소 리스트 추출
            List<ExcelData> addressDataList = excelService.parseExcelFile(file);

            // 주소를 위도/경도로 변환
            List<LocationDto> locations = getLocationsFromAddresses(addressDataList);

            model.addAttribute("locations", locations);
            model.addAttribute("googleMapsApiKey", AUTH_TOKEN);

            return "excel";
        } catch (Exception e) {
            model.addAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            model.addAttribute("googleMapsApiKey", AUTH_TOKEN);
            return "excel";
        }
    }

    // 사업단명과 주소 리스트를 위도/경도로 변환하는 메소드
    //
    private List<LocationDto> getLocationsFromAddresses(List<ExcelData> addressDataList) {
        List<LocationDto> locations = new ArrayList<>();

        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(AUTH_TOKEN)
                .build();

        try {
            for (ExcelData addressData : addressDataList) {
                String unitName = addressData.getFirst();
                String address = addressData.getSecond();
                try {
                    GeocodingResult[] response = GeocodingApi.geocode(context, unitName).await();

                    if (response != null && response.length > 0) {
                        locations.add(new LocationDto(
                                unitName,
                                address,
                                response[0].geometry.location.lat,
                                response[0].geometry.location.lng
                        ));
                    }

                    // API 호출 제한을 피하기 위해 약간의 딜레이 추가
                    Thread.sleep(100);
                } catch (Exception e) {
                    // 개별 주소 변환 실패시 로그만 남기고 계속 진행
                    System.err.println("주소 변환 실패: " + address + " - " + e.getMessage());
                }
            }
        } finally {
            context.shutdown();
        }

        return locations;
    }

    // 엑셀 파일의 위치 정보를 매개변수로 받는다. 해당 내용을 리스트 형태로 내보낸다.
    private List<LocationDto> getLocations() throws IOException, InterruptedException, ApiException {
        List<LocationDto> locations = new ArrayList<>();

        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(AUTH_TOKEN)
                .build();
        GeocodingResult[] response = GeocodingApi.geocode(context, // 1은?
                ADDRESS).await();
// Invoke .shutdown() after your application is done making requests
        context.shutdown();

        locations.add(new LocationDto(
                "",  // 사업단명 없음
                ADDRESS,
                response[0].geometry.location.lat,
                response[0].geometry.location.lng
        ));

        return locations;

    }

    @Data
    @AllArgsConstructor
    private static class LocationDto {
        private String businessUnit;  // 사업단명
        private String address;
        private double lat;
        private double lng;
    }
}

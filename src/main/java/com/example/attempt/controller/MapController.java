package com.example.attempt.controller;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


@Controller
public class MapController {

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

    @PostMapping("/map-excel")
    public String showMapXls(@RequestParam("file") MultipartFile file, Model model) {
        try {
            // 엑셀 파일에서 사업단명과 주소 리스트 추출
            List<AddressData> addressDataList = parseExcelFile(file);

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


    // 엑셀 파일에서 사업단명과 주소 리스트를 추출하는 메소드
    private List<AddressData> parseExcelFile(MultipartFile file) throws IOException {
        List<AddressData> addressDataList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용

            // 첫 번째 행을 제외하고 데이터 읽기 (헤더가 있다고 가정)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    // 첫 번째 열: 사업단명
                    Cell businessUnitCell = row.getCell(0);
                    String businessUnit = getCellValueAsString(businessUnitCell);

                    // 두 번째 열: 주소
                    Cell addressCell = row.getCell(1);
                    String address = getCellValueAsString(addressCell);

                    if (address != null && !address.trim().isEmpty()) {
                        addressDataList.add(new AddressData(
                            businessUnit != null ? businessUnit.trim() : "",
                            address.trim()
                        ));
                    }
                }
            }
        }

        return addressDataList;
    }

    // 셀 값을 문자열로 변환하는 헬퍼 메소드
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    // 사업단명과 주소 리스트를 위도/경도로 변환하는 메소드
    private List<LocationDto> getLocationsFromAddresses(List<AddressData> addressDataList) {
        List<LocationDto> locations = new ArrayList<>();

        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(AUTH_TOKEN)
                .build();

        try {
            for (AddressData addressData : addressDataList) {
                try {
                    GeocodingResult[] response = GeocodingApi.geocode(context, addressData.getAddress()).await();

                    if (response != null && response.length > 0) {
                        locations.add(new LocationDto(
                                addressData.getBusinessUnit(),
                                addressData.getAddress(),
                                response[0].geometry.location.lat,
                                response[0].geometry.location.lng
                        ));
                    }

                    // API 호출 제한을 피하기 위해 약간의 딜레이 추가
                    Thread.sleep(100);
                } catch (Exception e) {
                    // 개별 주소 변환 실패시 로그만 남기고 계속 진행
                    System.err.println("주소 변환 실패: " + addressData.getAddress() + " - " + e.getMessage());
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
    private static class AddressData {
        private String businessUnit;  // 사업단명
        private String address;       // 주소
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

package com.example.attempt.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

    /**
     * 엑셀의 1열과 2열의 값을 파싱합니다.
     * @param file 업로드된 엑셀 파일
     * @return 사업단명과 주소 리스트
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public List<ExcelData> parseExcelFile(MultipartFile file) throws IOException {
        List<ExcelData> addressDataList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용

            // 첫 번째 행을 제외하고 데이터 읽기 (헤더가 있다고 가정)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    // 첫 번째 열: 사업단명
                    Cell firstCell = row.getCell(0);
                    String first = getCellValueAsString(firstCell);

                    // 두 번째 열: 주소
                    Cell secondCell = row.getCell(1);
                    String second = getCellValueAsString(secondCell);

                    if (second != null && !second.trim().isEmpty()) {
                        addressDataList.add(new ExcelData(
                            first != null ? first.trim() : "",
                            second.trim()
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

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ExcelData {
        private String first;  // 사업단명 / 본인 이름
        private String second;       // 주소 / 사업단명


    }
}
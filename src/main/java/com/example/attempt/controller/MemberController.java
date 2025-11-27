package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.member.*;
import com.example.attempt.service.ExcelService;
import com.example.attempt.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.Data;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.attempt.service.ExcelService.memberExcelData;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final ExcelService excelService;

    /** CREATE */
    @PostMapping
    public ResponseEntity<CreateMemberResponse> create(@RequestBody CreateMemberRequest request) {

        Long id = memberService.create(
                request.getUsername(),
                request.getPhoneNumber(),
                request.getUnitName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateMemberResponse(id, request.getUsername()));
    }

    /** READ - 전체 조회 */
    @GetMapping
    public ResponseEntity<List<MemberDto>> all() {
        List<Member> members = memberService.findAll();

        List<MemberDto> result = members.stream()
                .map(m -> new MemberDto(
                        m.getId(),
                        m.getUsername(),
                        m.getPhoneNumber(),
                        m.getUnit() != null ? m.getUnit().getUnitName() : null
                )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** READ - 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> one(@PathVariable Long id) {
        Member m = memberService.findOne(id);
        if (m == null) return ResponseEntity.notFound().build();

        MemberDto dto = new MemberDto(
                m.getId(),
                m.getUsername(),
                m.getPhoneNumber(),
                m.getUnit() != null ? m.getUnit().getUnitName() : null
        );

        return ResponseEntity.ok(dto);
    }

    /** UPDATE */
    @PutMapping("/{id}")
    public ResponseEntity<MemberDto> update(
            @PathVariable Long id,
            @RequestBody UpdateMemberRequest request) {

        memberService.update(id, request.getUsername(), request.getPhoneNumber());
        Member updated = memberService.findOne(id);

        MemberDto dto = new MemberDto(
                updated.getId(),
                updated.getUsername(),
                updated.getPhoneNumber(),
                updated.getUnit() != null ? updated.getUnit().getUnitName() : null
        );

        return ResponseEntity.ok(dto);
    }

    /** DELETE */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 엑셀 업로드 */
    @PostMapping("/member-excel")
    public ResponseEntity<?> excel(@RequestParam("file") MultipartFile file) {
        try {
            List<memberExcelData> members = excelService.parseMemberFile(file);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("엑셀 처리 중 오류: " + e.getMessage());
        }
    }

    @PostMapping("/save-members")
    public ResponseEntity<SaveMembersResponse> saveMembers(@RequestBody SaveMembersRequest request) {
        int savedCount = memberService.saveMembersFromExcel(request.getMembers());

        return ResponseEntity.ok(
                new SaveMembersResponse(
                        savedCount,
                        savedCount + "명의 회원 정보가 성공적으로 저장되었습니다."
                )
        );
    }

    // 엑셀 저장 요청 DTO
    @Data
    public static class SaveMembersRequest {
        private List<memberExcelData> members;
    }

    // 엑셀 저장 응답 DTO
    @Data
    @AllArgsConstructor
    public static class SaveMembersResponse {
        private int savedCount;
        private String message;
    }


}

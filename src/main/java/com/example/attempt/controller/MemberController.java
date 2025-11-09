package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.service.ExcelService;
import com.example.attempt.service.MemberService;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.example.attempt.service.ExcelService.*;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final ExcelService excelService;

    // CREATE - 회원 등록
    @PostMapping
    public ResponseEntity<Member> createMember(@RequestBody Member member){
        Long memberId = memberService.join(member);
        Member savedMember = memberService.findOne(memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMember);
    }

    // READ - 전체 회원 조회
    @GetMapping
    public ResponseEntity<List<Member>> getAllMembers(){
        List<Member> members = memberService.findMembers();
        return ResponseEntity.ok(members);
    }

    // READ - 단건 회원 조회
    @GetMapping("/{id}")
    public ResponseEntity<Member> getMemberById(@PathVariable Long id){
        Member member = memberService.findOne(id);
        if (member == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(member);
    }

    // UPDATE - 회원 정보 수정
    @PutMapping("/{id}")
    public ResponseEntity<Member> updateMember(
            @PathVariable Long id,
            @RequestBody UpdateMemberRequest request){
        Member member = memberService.findOne(id);
        if (member == null) {
            return ResponseEntity.notFound().build();
        }
        memberService.update(id, request.getUsername(), request.getPhoneNumber());
        Member updatedMember = memberService.findOne(id);
        return ResponseEntity.ok(updatedMember);
    }

    // DELETE - 회원 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id){
        Member member = memberService.findOne(id);
        if (member == null) {
            return ResponseEntity.notFound().build();
        }
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/member-excel")
    public ResponseEntity<?> memberXls(@RequestParam("file") MultipartFile file) {
        try {
            // 엑셀 파일에서 사용자명과 전화번호, 사업단 명을 추출
            List<memberExcelData> members = excelService.parseMemberFile(file);
            return ResponseEntity.ok(new MemberExcelResponse(members));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("파일 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/save-members")
    public ResponseEntity<?> saveMembersFromExcel(@RequestBody SaveMembersRequest request) {
        try {
            int savedCount = memberService.saveMembersFromExcel(request.getMembers());
            return ResponseEntity.ok(new SaveMembersResponse(
                    savedCount,
                    savedCount + "명의 회원 정보가 성공적으로 저장되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("회원 정보 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // Member Excel 응답을 위한 DTO
    @Data
    @AllArgsConstructor
    public static class MemberExcelResponse {
        private List<memberExcelData> members;
    }

    // 에러 응답을 위한 DTO
    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }

    // 회원 정보 수정을 위한 DTO
    @Data
    public static class UpdateMemberRequest {
        private String username;
        private String phoneNumber;
    }

    // 회원 저장 요청을 위한 DTO
    @Data
    public static class SaveMembersRequest {
        private List<memberExcelData> members;
    }

    // 회원 저장 응답을 위한 DTO
    @Data
    @AllArgsConstructor
    public static class SaveMembersResponse {
        private int savedCount;
        private String message;
    }

}

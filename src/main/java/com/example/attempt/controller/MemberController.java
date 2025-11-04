package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.service.MemberService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

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

    // 회원 정보 수정을 위한 DTO
    @Data
    public static class UpdateMemberRequest {
        private String username;
        private String phoneNumber;
    }
}

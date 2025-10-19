package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/api/v1/member")
    public List<Member> member(Model model){
        Member member = new Member("신경준","010-1234-5678");
        memberService.join(member);
        return memberService.findMembers();
    }
}

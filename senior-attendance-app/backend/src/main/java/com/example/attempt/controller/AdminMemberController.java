package com.example.attempt.controller;

import com.example.attempt.dto.admin.MemberSummaryResponse;
import com.example.attempt.dto.admin.RegisterMemberRequest;
import com.example.attempt.dto.admin.RegisterMemberResponse;
import com.example.attempt.dto.admin.UpdateMemberActiveRequest;
import com.example.attempt.service.AdminMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @PostMapping
    public RegisterMemberResponse register(@Valid @RequestBody RegisterMemberRequest request) {
        return adminMemberService.register(request);
    }

    @GetMapping
    public List<MemberSummaryResponse> list() {
        return adminMemberService.list();
    }

    @PatchMapping("/{employeeId}")
    public void updateActive(@PathVariable Long employeeId, @Valid @RequestBody UpdateMemberActiveRequest request) {
        adminMemberService.updateActive(employeeId, request.getActive());
    }
}

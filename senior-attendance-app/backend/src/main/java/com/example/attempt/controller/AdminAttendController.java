package com.example.attempt.controller;

import com.example.attempt.domain.Attend;
import com.example.attempt.dto.admin.AttendeeItem;
import com.example.attempt.dto.admin.UpdateAttendStatusRequest;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.service.AttendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/attend")
@RequiredArgsConstructor
public class AdminAttendController {

    private final AttendService attendService;
    private final AttendRepository attendRepository;

    @PatchMapping("/{attendId}")
    public AttendeeItem update(@PathVariable Long attendId, @Valid @RequestBody UpdateAttendStatusRequest request) {
        attendService.updateAttendStatus(attendId, request.getStatus(), request.getNote());

        Attend attend = attendRepository.findByIdWithMember(attendId)
                .orElseThrow(() -> new ResourceNotFoundException("출석 정보를 찾을 수 없습니다. ID: " + attendId));

        return new AttendeeItem(
                attend.getId(),
                attend.getMember().getId(),
                attend.getMember().getUsername(),
                attend.getStatus(),
                attend.getNote(),
                attend.getAttendedAt());
    }
}

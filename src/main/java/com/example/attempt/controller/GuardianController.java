package com.example.attempt.controller;

import com.example.attempt.domain.Guardian;
import com.example.attempt.dto.guardian.CreateGuardianRequest;
import com.example.attempt.dto.guardian.CreateGuardianResponse;
import com.example.attempt.dto.guardian.GuardianDto;
import com.example.attempt.service.GuardianService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/guardian")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;

    /** 보호자 등록 */
    @PostMapping
    public ResponseEntity<CreateGuardianResponse> createGuardian(
            @RequestBody CreateGuardianRequest req) {

        Long guardianId = guardianService.create(
                req.getMemberId(),
                req.getName(),
                req.getPhone(),
                req.getReceiveNotification()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateGuardianResponse(guardianId));
    }

    /** 특정 회원의 보호자 목록 조회 */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<GuardianDto>> getGuardians(@PathVariable Long memberId) {

        List<Guardian> list = guardianService.findByMember(memberId);

        List<GuardianDto> result = list.stream()
                .map(g -> new GuardianDto(
                        g.getId(),
                        g.getName(),
                        g.getPhone(),
                        g.getReceiveNotification()
                )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** 단건 보호자 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<GuardianDto> getOne(@PathVariable Long id) {

        Guardian g = guardianService.findOne(id);
        if (g == null) {
            return ResponseEntity.notFound().build();
        }

        GuardianDto dto = new GuardianDto(
                g.getId(),
                g.getName(),
                g.getPhone(),
                g.getReceiveNotification()
        );

        return ResponseEntity.ok(dto);
    }
}

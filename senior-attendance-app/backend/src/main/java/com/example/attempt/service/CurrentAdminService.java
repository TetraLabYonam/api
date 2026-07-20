package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * JWT 인증 주체(관리자 username)로부터 현재 로그인한 Admin을 조회한다.
 * CurrentMemberService와 동일한 패턴 — 컨트롤러가 요청 바디의 adminId를 신뢰하지 않도록 한다.
 */
@Service
@RequiredArgsConstructor
public class CurrentAdminService {

    private final AdminRepository adminRepository;

    public Admin getCurrentAdmin() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("인증된 관리자를 찾을 수 없습니다. username=" + username));
    }
}

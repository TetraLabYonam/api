package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentMemberServiceTest {

    private MemberRepository memberRepository;
    private CurrentMemberService service;

    @BeforeEach
    void setup() {
        memberRepository = mock(MemberRepository.class);
        service = new CurrentMemberService(memberRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentMember_looksUpByAuthenticatedEmployeeId() {
        Member member = Member.withPhoneNumberHash("김할매", "hashed");
        member.setEmployeeId(1001L);
        when(memberRepository.findByEmployeeId(1001L)).thenReturn(Optional.of(member));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1001", null, List.of()));

        Member result = service.getCurrentMember();

        assertEquals(1001L, result.getEmployeeId());
    }

    @Test
    void getCurrentMember_throws_whenNoMemberForEmployeeId() {
        when(memberRepository.findByEmployeeId(9999L)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("9999", null, List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentMember());
    }
}

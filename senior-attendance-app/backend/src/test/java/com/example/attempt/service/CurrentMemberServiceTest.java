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
    void getCurrentMember_looksUpByAuthenticatedPhoneNumber() {
        Member member = new Member("김할매", "01012345678");
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01012345678", null, List.of()));

        Member result = service.getCurrentMember();

        assertEquals("01012345678", result.getPhoneNumber());
    }

    @Test
    void getCurrentMember_throws_whenNoMemberForPhoneNumber() {
        when(memberRepository.findByPhoneNumber("01099990000")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01099990000", null, List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentMember());
    }
}

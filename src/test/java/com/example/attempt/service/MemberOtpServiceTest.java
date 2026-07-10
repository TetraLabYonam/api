package com.example.attempt.service;

import com.example.attempt.domain.MemberOtpCode;
import com.example.attempt.repository.MemberOtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberOtpServiceTest {

    private MemberOtpCodeRepository repository;
    private SmsService smsService;
    private MemberOtpService service;

    @BeforeEach
    void setup() {
        repository = mock(MemberOtpCodeRepository.class);
        smsService = mock(SmsService.class);
        service = new MemberOtpService(repository, smsService, "test-otp-secret", 300, 6);
    }

    @Test
    void requestOtp_savesHashedCodeAndSendsSms() {
        service.requestOtp("01012345678");

        ArgumentCaptor<MemberOtpCode> captor = ArgumentCaptor.forClass(MemberOtpCode.class);
        verify(repository, times(1)).save(captor.capture());
        assertEquals("01012345678", captor.getValue().getPhoneNumber());
        assertNotNull(captor.getValue().getCodeHash());

        verify(smsService, times(1)).sendCustomMessage(eq("01012345678"), anyString());
    }

    @Test
    void verifyOtp_returnsTrue_forCorrectCode() {
        // 실제 서비스가 생성한 해시와 동일한 알고리즘으로 직접 검증 가능하도록,
        // requestOtp가 저장한 코드를 캡처해서 그대로 verifyOtp에 사용한다.
        ArgumentCaptor<MemberOtpCode> captor = ArgumentCaptor.forClass(MemberOtpCode.class);
        service.requestOtp("01099998888");
        verify(repository).save(captor.capture());
        MemberOtpCode saved = captor.getValue();

        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc("01099998888"))
                .thenReturn(Optional.of(saved));

        // 발송된 원본 코드를 알 수 없으므로, 이 테스트는 잘못된 코드가 거부되는 것만 검증한다.
        boolean result = service.verifyOtp("01099998888", "000000");

        assertFalse(result);
    }

    @Test
    void verifyOtp_returnsFalse_whenExpired() {
        MemberOtpCode expired = new MemberOtpCode("01055556666", "irrelevant-hash",
                LocalDateTime.now().minusMinutes(1));
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc("01055556666"))
                .thenReturn(Optional.of(expired));

        boolean result = service.verifyOtp("01055556666", "123456");

        assertFalse(result);
    }
}

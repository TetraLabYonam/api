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
    void verifyOtp_returnsTrue_forCorrectCode_andFalse_forWrongCode() {
        // requestOtp가 저장하는 해시(캡처)와 SMS로 발송하는 평문 코드(캡처)는 같은 요청에서
        // 나온 한 쌍이므로, SMS 본문에서 평문 코드를 추출해 실제 "정답 코드가 통과하는지"를
        // 검증한다. 서비스의 해시 알고리즘을 테스트에서 재구현하지 않고도 성공 경로를 검증할 수 있다.
        ArgumentCaptor<MemberOtpCode> otpCaptor = ArgumentCaptor.forClass(MemberOtpCode.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.requestOtp("01099998888");

        verify(repository).save(otpCaptor.capture());
        verify(smsService).sendCustomMessage(eq("01099998888"), messageCaptor.capture());

        MemberOtpCode saved = otpCaptor.getValue();
        String sentMessage = messageCaptor.getValue();
        String actualCode = sentMessage.replaceAll(".*인증번호는 (\\d+) .*", "$1");
        assertEquals(6, actualCode.length());

        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc("01099998888"))
                .thenReturn(Optional.of(saved));

        assertTrue(service.verifyOtp("01099998888", actualCode));
        assertFalse(service.verifyOtp("01099998888", "000000"));
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

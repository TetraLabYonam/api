package com.example.attempt.support;

import com.example.attempt.domain.Member;
import com.example.attempt.repository.MemberRepository;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * 통합 테스트 공용 헬퍼: employeeId+phoneNumber 조합으로 Member를 만들고 실제
 * /api/v1/member-auth/login 호출을 태워 ROLE_MEMBER accessToken을 얻는다.
 */
public class MemberAuthTestSupport {
    public static String loginAsMember(TestRestTemplate restTemplate, int port,
            MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            String username, String phoneNumber) {
        Member member = Member.withPhoneNumberHash(username, passwordEncoder.encode(phoneNumber));
        member.setEmployeeId(memberRepository.findMaxEmployeeIdOrDefault() + 1);
        member = memberRepository.save(member);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("employeeId", member.getEmployeeId(), "phoneNumber", phoneNumber), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/member-auth/login", req, Map.class);
        return (String) resp.getBody().get("accessToken");
    }
}

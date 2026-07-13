package com.example.attempt.repository;

import com.example.attempt.domain.MemberOtpCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MemberOtpCodeRepositoryTest {

    @Autowired
    private MemberOtpCodeRepository repository;

    @Test
    void findTopByPhoneNumberOrderByCreatedAtDesc_returnsMostRecent() throws InterruptedException {
        MemberOtpCode older = new MemberOtpCode("01011112222", "hash-old",
                LocalDateTime.now().plusMinutes(5));
        repository.saveAndFlush(older);
        Thread.sleep(10);
        MemberOtpCode newer = new MemberOtpCode("01011112222", "hash-new",
                LocalDateTime.now().plusMinutes(5));
        repository.saveAndFlush(newer);

        Optional<MemberOtpCode> result = repository.findTopByPhoneNumberOrderByCreatedAtDesc("01011112222");

        assertTrue(result.isPresent());
        assertEquals("hash-new", result.get().getCodeHash());
    }
}

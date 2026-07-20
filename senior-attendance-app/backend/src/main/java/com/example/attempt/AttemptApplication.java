package com.example.attempt;

import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AttemptApplication implements CommandLineRunner {

    Logger logger = LoggerFactory.getLogger(AttemptApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(AttemptApplication.class, args);
	}

    private final MemberRepository memberRepository;
    private final AttendRepository attendRepository;
    private final PlaceRepository placeRepository;

    public AttemptApplication(MemberRepository memberRepository, AttendRepository attendRepository, PlaceRepository placeRepository) {
        this.memberRepository = memberRepository;
        this.attendRepository = attendRepository;
        this.placeRepository = placeRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Attempt Application Started");
    }
}

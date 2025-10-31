package com.example.attempt;

import com.example.attempt.Repository.AttendRepository;
import com.example.attempt.Repository.MemberRepository;
import com.example.attempt.Repository.PlaceRepository;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import com.example.attempt.service.MemberService;
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

    private final MemberService memberService;
    private final AttendRepository attendRepository;
    private final PlaceRepository placeRepository;

    public AttemptApplication(MemberService memberService, AttendRepository attendRepository, PlaceRepository placeRepository) {
        this.memberService = memberService;
        this.attendRepository = attendRepository;
        this.placeRepository = placeRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        Member member1 = new Member("신경준","010-1234-5678");
        memberService.join(member1);

        Attend attend = new Attend();



        logger.info("Attempt Application Started");
    }
}

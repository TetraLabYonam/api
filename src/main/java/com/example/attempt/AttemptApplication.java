package com.example.attempt;

import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
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
        Member member1 = new Member("신경준","010-1234-5678", "010-1111-2222");
        Member member2 = new Member("홍길동","010-5678-3456","010-1111-5555");
        Member member3 = new Member("김철수","010-1111-1111","");

        Attend attend = new Attend();



        logger.info("Attempt Application Started");
    }
}

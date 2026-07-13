package com.example.attempt.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;

/***
 * 회원은 출석을 할 수 있다. - 출석 여부는 관리자가 관리한다.
 * 회원은 본인 스케줄을 확인할 수 있다.
 *
 */

@Entity
@NoArgsConstructor
@Table
@Getter
@Setter
public class Member {

    @Id @GeneratedValue
    @Column(name = "MEMBER_ID")
    private Long id; // 직번으로 기본키 설정 여부
    private String username;
    private String phoneNumber;
    private String guardianPhone;

    @OneToMany(mappedBy = "member")
    @JsonIgnore // prevent lazy-loaded attends from being serialized
    private List<Attend> attends = new ArrayList<>();

    @Embedded
    private Unit unit;

    @Column(name = "location_consent_agreed_at")
    private LocalDateTime locationConsentAgreedAt;

    @Column(name = "assigned_place_id")
    private Long assignedPlaceId;

    public Member(String username, String phoneNumber) {
        this.username = username;
        this.phoneNumber = phoneNumber;
    }

    public Member(String username, String phoneNumber, String guardianPhone) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.guardianPhone = guardianPhone;
    }

}

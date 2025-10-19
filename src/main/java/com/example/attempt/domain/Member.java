package com.example.attempt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 * 회원은 출석을 할 수 있다. - 출석 여부는 관리자가 관리한다.
 * 회원은 본인 스케줄을 확인할 수 있다.
 *
 */

@Entity
@Data
@NoArgsConstructor
@Table
public class Member {

    @Id @GeneratedValue
    private Long id; // 직번으로 기본키 설정 여부
    private String username;
    private String phoneNumber;

    public Member(String username, String phoneNumber) {
        this.username = username;
        this.phoneNumber = phoneNumber;
    }
}

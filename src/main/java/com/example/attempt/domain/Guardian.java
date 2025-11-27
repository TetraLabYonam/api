package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Guardian {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String phone;

    // 문자 수신 여부
    private Boolean receiveNotification = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID")
    private Member member;

    public Guardian() {}

    public Guardian(String name, String phone, Boolean receiveNotification, Member member) {
        this.name = name;
        this.phone = phone;
        this.receiveNotification = receiveNotification;
        this.member = member;
    }
}

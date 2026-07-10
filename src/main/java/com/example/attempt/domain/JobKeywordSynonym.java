package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "JOB_KEYWORD_SYNONYM")
@Getter
@NoArgsConstructor
public class JobKeywordSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLACE_ID", nullable = false)
    private Place place;

    @Column(name = "KEYWORD", nullable = false, length = 100)
    private String keyword;

    public JobKeywordSynonym(Place place, String keyword) {
        this.place = place;
        this.keyword = keyword;
    }
}

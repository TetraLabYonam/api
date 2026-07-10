package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_keyword_synonym")
@Getter
@NoArgsConstructor
public class JobKeywordSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 100)
    private String keyword;

    public JobKeywordSynonym(Place place, String keyword) {
        this.place = place;
        this.keyword = keyword;
    }
}

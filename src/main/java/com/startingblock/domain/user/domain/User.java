package com.startingblock.domain.user.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import com.startingblock.domain.common.BaseEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name="user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "birth")
    private LocalDate birth;

    @Column(name = "is_completed_business_registration", nullable = false)
    private Boolean isCompletedBusinessRegistration;

    @Column(name = "residence")
    private String residence;

    @Column(name = "university")
    private String university;

    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "profileNumber", nullable = false)
    private Integer profileNumber;

    @Builder
    public User(String nickname, LocalDate birth, String residence, String university, String email, Provider provider, String providerId, Role role) {
        this.nickname = nickname;
        this.birth = birth;
        this.isCompletedBusinessRegistration = false;
        this.residence = residence;
        this.university = university;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
        this.profileNumber = 0;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateBirth(LocalDate birth) {
        this.birth = birth;
    }

    public void updateIsCompletedBusinessRegistration(Boolean isCompletedBusinessRegistration) {
        this.isCompletedBusinessRegistration = isCompletedBusinessRegistration;
    }

    public void updateResidence(String residence) {
        this.residence = residence;
    }

    public void updateUniversity(String university) {
        this.university = university;
    }

    public void updateProfileNumber(Integer profileNumber) {
        this.profileNumber = profileNumber;
    }

}

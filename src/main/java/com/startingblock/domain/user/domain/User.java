package com.startingblock.domain.user.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import com.startingblock.domain.common.BaseEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import lombok.Builder;
import lombok.Getter;

@Entity
@Table(name="user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Builder
    public User(String name, String email, Provider provider, Role role, String providerId) {
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.role = role;
        this.providerId = providerId;
    }

    public void updateName(String name){
        this.name = name;
    }

}

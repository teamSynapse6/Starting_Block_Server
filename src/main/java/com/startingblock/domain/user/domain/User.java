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

    private String name;

    @Email
    private String email;

    private String imageUrl;

    private String password;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String providerId;

    @Builder
    public User(Long id, String name, String email, String imageUrl, String password, Provider provider, Role role, String providerId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
        this.password = password;
        this.provider = provider;
        this.role = role;
        this.providerId = providerId;
    }

    public void updateName(String name){
        this.name = name;
    }

    public void updateImageUrl(String imageUrl){
        this.imageUrl = imageUrl;
    }

}

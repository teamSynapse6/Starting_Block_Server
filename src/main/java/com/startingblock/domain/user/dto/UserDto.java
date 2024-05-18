package com.startingblock.domain.user.dto;

import com.startingblock.domain.user.domain.Provider;
import com.startingblock.domain.user.domain.Role;
import com.startingblock.domain.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class UserDto {

    private String nickname;
    private LocalDate birth;
    private Boolean isCompletedBusinessRegistration;
    private String residence;
    private String university;
    private String email;
    private Provider provider;
    private String providerId;
    private Role role;
    private Integer profileNumber;

    public static UserDto toDto(User user) {
        return UserDto.builder()
                .nickname(user.getNickname())
                .birth(user.getBirth())
                .isCompletedBusinessRegistration(user.getIsCompletedBusinessRegistration())
                .residence(user.getResidence())
                .university(user.getUniversity())
                .email(user.getEmail())
                .provider(user.getProvider())
                .providerId(user.getProviderId())
                .role(user.getRole())
                .profileNumber(user.getProfileNumber())
                .build();
    }

}

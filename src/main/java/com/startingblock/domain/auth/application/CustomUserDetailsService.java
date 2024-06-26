package com.startingblock.domain.auth.application;

import java.util.Optional;

import com.startingblock.domain.common.Status;
import com.startingblock.global.DefaultAssert;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;

import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmailAndStatus(email, Status.ACTIVE)
                .orElseThrow(() ->
                        new UsernameNotFoundException("유저 정보를 찾을 수 없습니다.")
                );

        return UserPrincipal.create(user);
    }

    @Transactional
    public UserDetails loadUserById(Long id) {
        Optional<User> user = userRepository.findById(id);
        DefaultAssert.isOptionalPresent(user);

        return UserPrincipal.create(user.get());
    }

    @Transactional
    public UserDetails loadUserByProviderId(final String providerId) {
        Optional<User> user = userRepository.findByProviderIdAndStatus(providerId, Status.ACTIVE);
        DefaultAssert.isOptionalPresent(user);

        return UserPrincipal.create(user.get());
    }

}

package com.startingblock.domain.user.domain.repository;

import java.util.Optional;

import com.startingblock.domain.common.Status;
import com.startingblock.domain.user.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Long>{

    Optional<User> findByEmailAndStatus(String email, Status status);

    Optional<User> findByProviderIdAndStatus(String providerId, Status status);

}

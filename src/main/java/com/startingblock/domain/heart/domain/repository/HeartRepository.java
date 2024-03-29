package com.startingblock.domain.heart.domain.repository;

import com.startingblock.domain.heart.domain.Heart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeartRepository extends JpaRepository<Heart, Long> {

}

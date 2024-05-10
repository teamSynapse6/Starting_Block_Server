package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
}

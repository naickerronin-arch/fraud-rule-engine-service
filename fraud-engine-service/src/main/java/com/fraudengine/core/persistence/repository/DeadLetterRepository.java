package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.DeadLetterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, Long> {
}

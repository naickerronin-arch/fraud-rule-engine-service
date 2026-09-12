package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.BadLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface BadLocationRepository extends JpaRepository<BadLocation, Long> {
    @Modifying
    @Query(
            value = """
                INSERT INTO bad_locations (area_code, level, created_at)
                VALUES (:areaCode, :level, :updatedAt)
                ON CONFLICT (area_code) DO UPDATE SET level = :level, created_at = :updatedAt
                """,
            nativeQuery = true)
    void upsertLevel(
            @Param("areaCode") String areaCode,
            @Param("level") int level,
            @Param("updatedAt") Instant updatedAt);
}

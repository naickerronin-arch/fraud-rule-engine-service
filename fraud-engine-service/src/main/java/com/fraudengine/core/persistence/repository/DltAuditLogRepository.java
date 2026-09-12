package com.fraudengine.core.persistence.repository;

import com.fraudengine.core.persistence.entity.DltAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DltAuditLogRepository extends JpaRepository<DltAuditLog, Long> {
}

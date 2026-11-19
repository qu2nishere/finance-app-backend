package com.quan.reportservice.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
}

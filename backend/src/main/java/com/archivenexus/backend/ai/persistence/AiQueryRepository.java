package com.archivenexus.backend.ai.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiQueryRepository extends JpaRepository<AiQueryEntity, String> {
    List<AiQueryEntity> findAllByOrderByCreatedAtDesc();

    Optional<AiQueryEntity> findFirstByOrderByCreatedAtDesc();
}

package com.archivenexus.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorStateRepository extends JpaRepository<SimulatorStateEntity, String> {
}

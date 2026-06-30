package com.archivenexus.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "simulator_control_state")
public class SimulatorControlStateEntity {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false)
    private boolean running;

    @Column(nullable = false)
    private long tick;

    @Column(name = "last_parallel_worker_count", nullable = false)
    private int lastParallelWorkerCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SimulatorControlStateEntity() {
    }

    public SimulatorControlStateEntity(
            String id, boolean running, long tick, int lastParallelWorkerCount, Instant updatedAt
    ) {
        this.id = id;
        this.running = running;
        this.tick = tick;
        this.lastParallelWorkerCount = lastParallelWorkerCount;
        this.updatedAt = updatedAt;
    }

    public boolean running() { return running; }
    public long tick() { return tick; }
    public int lastParallelWorkerCount() { return lastParallelWorkerCount; }
    public Instant updatedAt() { return updatedAt; }
}

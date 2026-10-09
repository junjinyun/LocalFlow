package com.localflow.domain.agent.entity;

import com.localflow.domain.agent.domain.AgentProgressStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "agent_run_progress_events", indexes = {
        @Index(name = "idx_agent_run_progress_run_id", columnList = "run_id")
})
public class AgentRunProgressEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AgentRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AgentProgressStage stage;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentRunProgressEvent() {
    }

    public static AgentRunProgressEvent create(AgentRun run, AgentProgressStage stage, String message) {
        AgentRunProgressEvent event = new AgentRunProgressEvent();
        event.run = run;
        event.stage = stage;
        event.message = message;
        event.createdAt = Instant.now();
        return event;
    }

    public Long getId() { return id; }
    public AgentProgressStage getStage() { return stage; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}

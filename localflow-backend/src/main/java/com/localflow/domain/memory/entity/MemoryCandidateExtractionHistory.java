package com.localflow.domain.memory.entity;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionStatus;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "memory_candidate_extraction_history")
public class MemoryCandidateExtractionHistory {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemoryCandidateExtractionTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MemoryCandidateSourceType sourceType;

    @Column(length = 36)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemoryCandidateExtractionStatus status;

    @Column(nullable = false)
    private int createdCount;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant completedAt;

    protected MemoryCandidateExtractionHistory() {
    }

    public static MemoryCandidateExtractionHistory success(
            Project project, MemoryCandidateExtractionTrigger triggerType,
            MemoryCandidateSourceType sourceType, String sourceId, int createdCount,
            Instant startedAt, Instant completedAt) {
        return create(project, triggerType, sourceType, sourceId,
                MemoryCandidateExtractionStatus.SUCCESS, createdCount, null, startedAt, completedAt);
    }

    public static MemoryCandidateExtractionHistory failure(
            Project project, MemoryCandidateExtractionTrigger triggerType,
            MemoryCandidateSourceType sourceType, String sourceId, String errorMessage,
            Instant startedAt, Instant completedAt) {
        String safeMessage = errorMessage == null || errorMessage.isBlank()
                ? "기억 후보 추출 중 알 수 없는 오류가 발생했습니다." : errorMessage.strip();
        if (safeMessage.length() > 1000) safeMessage = safeMessage.substring(0, 1000);
        return create(project, triggerType, sourceType, sourceId,
                MemoryCandidateExtractionStatus.FAILED, 0, safeMessage, startedAt, completedAt);
    }

    private static MemoryCandidateExtractionHistory create(
            Project project, MemoryCandidateExtractionTrigger triggerType,
            MemoryCandidateSourceType sourceType, String sourceId,
            MemoryCandidateExtractionStatus status, int createdCount, String errorMessage,
            Instant startedAt, Instant completedAt) {
        MemoryCandidateExtractionHistory history = new MemoryCandidateExtractionHistory();
        history.id = UUID.randomUUID().toString();
        history.project = project;
        history.triggerType = triggerType;
        history.sourceType = sourceType;
        history.sourceId = sourceId;
        history.status = status;
        history.createdCount = Math.max(0, createdCount);
        history.errorMessage = errorMessage;
        history.startedAt = startedAt;
        history.completedAt = completedAt;
        return history;
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public MemoryCandidateExtractionTrigger getTriggerType() { return triggerType; }
    public MemoryCandidateSourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public MemoryCandidateExtractionStatus getStatus() { return status; }
    public int getCreatedCount() { return createdCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}

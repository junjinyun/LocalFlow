package com.localflow.domain.memory.entity;

import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.domain.MemoryCandidateStatus;
import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.project.entity.Project;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "project_memory_candidates", uniqueConstraints = @UniqueConstraint(
        name = "uk_memory_candidate_source",
        columnNames = {"project_id", "source_type", "source_id", "source_order"}))
public class ProjectMemoryCandidate {
    @Id @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private MemoryCandidateSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 36)
    private String sourceId;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    @Column(nullable = false, length = 500)
    private String sourceExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemoryCandidateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MemoryType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 10000)
    private String content;

    @Column(nullable = false)
    private boolean globalScope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_candidate_scope_tags", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> scopeTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_candidate_scope_paths", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "path_pattern", nullable = false, length = 300)
    private Set<String> scopeFilePatterns = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_candidate_scope_tasks", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "task_type", nullable = false, length = 40)
    private Set<String> scopeTaskTypes = new LinkedHashSet<>();

    @Column(length = 36)
    private String possibleDuplicateMemoryId;

    @Column(length = 36)
    private String approvedMemoryId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant reviewedAt;

    protected ProjectMemoryCandidate() {
    }

    public static ProjectMemoryCandidate create(Project project, MemoryCandidateSourceType sourceType,
                                                String sourceId, int sourceOrder, String sourceExcerpt,
                                                MemoryType type, String title, String content,
                                                boolean globalScope, Collection<String> scopeTags,
                                                Collection<String> scopeFilePatterns,
                                                Collection<String> scopeTaskTypes,
                                                String possibleDuplicateMemoryId) {
        ProjectMemoryCandidate candidate = new ProjectMemoryCandidate();
        candidate.id = UUID.randomUUID().toString();
        candidate.project = project;
        candidate.sourceType = sourceType;
        candidate.sourceId = sourceId;
        candidate.sourceOrder = sourceOrder;
        candidate.sourceExcerpt = sourceExcerpt;
        candidate.status = MemoryCandidateStatus.PENDING;
        candidate.type = type;
        candidate.title = title;
        candidate.content = content;
        candidate.globalScope = globalScope;
        if (scopeTags != null) candidate.scopeTags.addAll(scopeTags);
        if (scopeFilePatterns != null) candidate.scopeFilePatterns.addAll(scopeFilePatterns);
        if (scopeTaskTypes != null) candidate.scopeTaskTypes.addAll(scopeTaskTypes);
        candidate.possibleDuplicateMemoryId = possibleDuplicateMemoryId;
        candidate.createdAt = Instant.now();
        return candidate;
    }

    public void approve(String memoryId) {
        requirePending();
        this.status = MemoryCandidateStatus.APPROVED;
        this.approvedMemoryId = memoryId;
        this.reviewedAt = Instant.now();
    }

    public void reject() {
        requirePending();
        this.status = MemoryCandidateStatus.REJECTED;
        this.reviewedAt = Instant.now();
    }

    private void requirePending() {
        if (status != MemoryCandidateStatus.PENDING) {
            throw new IllegalStateException("이미 검토된 프로젝트 기억 후보입니다.");
        }
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public MemoryCandidateSourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public int getSourceOrder() { return sourceOrder; }
    public String getSourceExcerpt() { return sourceExcerpt; }
    public MemoryCandidateStatus getStatus() { return status; }
    public MemoryType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isGlobalScope() { return globalScope; }
    public Set<String> getScopeTags() { return Set.copyOf(scopeTags); }
    public Set<String> getScopeFilePatterns() { return Set.copyOf(scopeFilePatterns); }
    public Set<String> getScopeTaskTypes() { return Set.copyOf(scopeTaskTypes); }
    public String getPossibleDuplicateMemoryId() { return possibleDuplicateMemoryId; }
    public String getApprovedMemoryId() { return approvedMemoryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
}

package com.localflow.domain.agent.entity;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "agent_runs")
public class AgentRun {
    @Id @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Column(nullable = false, length = 10000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiProviderType preferredGenerationProvider;

    @Column(length = 150)
    private String preferredModel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiProviderType actualGenerationProvider;

    @Column(length = 150)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentRunStatus status;

    @Lob
    private String decompositionJson;

    @Lob
    private String decisionJson;

    @Lob
    private String planJson;

    @Lob
    private String changesJson;

    @Lob
    private String inputSnapshotJson;

    @Lob
    private String resultMessage;

    @Column(length = 2000)
    private String errorMessage;

    private Integer inputTokens;
    private Integer outputTokens;

    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AgentRun() {
    }

    public static AgentRun draft(Project project, String prompt, ExecutionMode executionMode,
                                 AiProviderType preferredGenerationProvider, String preferredModel) {
        AgentRun run = new AgentRun();
        run.id = UUID.randomUUID().toString();
        run.project = project;
        run.prompt = prompt;
        run.executionMode = executionMode;
        run.preferredGenerationProvider = preferredGenerationProvider;
        run.preferredModel = preferredModel == null || preferredModel.isBlank()
                ? null : preferredModel.strip();
        run.status = AgentRunStatus.DRAFT;
        run.createdAt = Instant.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public void cancel() {
        this.status = AgentRunStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void queue() {
        this.status = AgentRunStatus.PENDING;
        this.errorMessage = null;
        this.completedAt = null;
        touch();
    }

    public void startDeciding() {
        this.status = AgentRunStatus.DECIDING;
        touch();
    }

    public void startPlanning(AiProviderType provider) {
        this.actualGenerationProvider = provider;
        this.status = AgentRunStatus.PLANNING;
        touch();
    }

    public void recordDecomposition(String decompositionJson, AiProviderType provider, String model,
                                    Integer inputTokens, Integer outputTokens) {
        this.decompositionJson = decompositionJson;
        this.actualGenerationProvider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        touch();
    }

    public void waitForApproval(String decisionJson, String planJson, String changesJson, String message,
                                String model, Integer inputTokens, Integer outputTokens) {
        this.decisionJson = decisionJson;
        this.planJson = planJson;
        this.changesJson = changesJson;
        this.resultMessage = message;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = AgentRunStatus.WAITING_APPROVAL;
        touch();
    }

    public void startRunning(String changesJson) {
        this.changesJson = changesJson;
        this.status = AgentRunStatus.RUNNING;
        touch();
    }

    public void startValidating() {
        this.status = AgentRunStatus.VALIDATING;
        touch();
    }

    public void recordChanges(String changesJson) {
        this.changesJson = changesJson;
        touch();
    }

    public void recordInputSnapshot(String inputSnapshotJson) {
        this.inputSnapshotJson = inputSnapshotJson;
        touch();
    }

    public void complete(String decisionJson, String planJson, String message,
                         String model, Integer inputTokens, Integer outputTokens) {
        this.decisionJson = decisionJson;
        this.planJson = planJson;
        this.resultMessage = message;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.errorMessage = null;
        this.status = AgentRunStatus.COMPLETED;
        this.completedAt = Instant.now();
        touch();
    }

    public void fail(String message) {
        this.errorMessage = message == null ? "알 수 없는 실행 오류" : message;
        this.status = AgentRunStatus.FAILED;
        this.completedAt = Instant.now();
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public String getPrompt() { return prompt; }
    public ExecutionMode getExecutionMode() { return executionMode; }
    public AiProviderType getPreferredGenerationProvider() { return preferredGenerationProvider; }
    public String getPreferredModel() { return preferredModel; }
    public AiProviderType getActualGenerationProvider() { return actualGenerationProvider; }
    public String getModel() { return model; }
    public AgentRunStatus getStatus() { return status; }
    public String getDecompositionJson() { return decompositionJson; }
    public String getDecisionJson() { return decisionJson; }
    public String getPlanJson() { return planJson; }
    public String getChangesJson() { return changesJson; }
    public String getInputSnapshotJson() { return inputSnapshotJson; }
    public String getResultMessage() { return resultMessage; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}

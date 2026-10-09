package com.localflow.domain.project.entity;

import com.localflow.domain.agent.domain.ExecutionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "project_settings")
public class ProjectSettings {
    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ExecutionMode executionMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PrivacyMode privacyMode;
    @Enumerated(EnumType.STRING) private PermissionPolicy readPolicy;
    @Enumerated(EnumType.STRING) private PermissionPolicy createPolicy;
    @Enumerated(EnumType.STRING) private PermissionPolicy editPolicy;
    @Enumerated(EnumType.STRING) private PermissionPolicy movePolicy;
    @Enumerated(EnumType.STRING) private PermissionPolicy deletePolicy;
    @Enumerated(EnumType.STRING) private PermissionPolicy executePolicy;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProjectSettings() {
    }

    public static ProjectSettings defaults(Project project) {
        ProjectSettings settings = new ProjectSettings();
        settings.id = UUID.randomUUID().toString();
        settings.project = project;
        settings.executionMode = ExecutionMode.BALANCED;
        settings.privacyMode = PrivacyMode.LOCAL_ONLY;
        settings.readPolicy = PermissionPolicy.ALLOW;
        settings.createPolicy = PermissionPolicy.CONFIRM;
        settings.editPolicy = PermissionPolicy.CONFIRM;
        settings.movePolicy = PermissionPolicy.CONFIRM;
        settings.deletePolicy = PermissionPolicy.CONFIRM;
        settings.executePolicy = PermissionPolicy.DENY;
        settings.updatedAt = Instant.now();
        return settings;
    }

    public void update(ExecutionMode executionMode, PrivacyMode privacyMode,
                       PermissionPolicy readPolicy, PermissionPolicy createPolicy,
                       PermissionPolicy editPolicy, PermissionPolicy movePolicy,
                       PermissionPolicy deletePolicy, PermissionPolicy executePolicy) {
        this.executionMode = executionMode;
        this.privacyMode = privacyMode;
        this.readPolicy = readPolicy;
        this.createPolicy = createPolicy;
        this.editPolicy = editPolicy;
        this.movePolicy = movePolicy;
        this.deletePolicy = deletePolicy;
        this.executePolicy = executePolicy;
        this.updatedAt = Instant.now();
    }

    public String getProjectId() { return project.getId(); }
    public ExecutionMode getExecutionMode() { return executionMode; }
    public PrivacyMode getPrivacyMode() { return privacyMode; }
    public PermissionPolicy getReadPolicy() { return readPolicy; }
    public PermissionPolicy getCreatePolicy() { return createPolicy; }
    public PermissionPolicy getEditPolicy() { return editPolicy; }
    public PermissionPolicy getMovePolicy() { return movePolicy; }
    public PermissionPolicy getDeletePolicy() { return deletePolicy; }
    public PermissionPolicy getExecutePolicy() { return executePolicy; }
    public Instant getUpdatedAt() { return updatedAt; }
}

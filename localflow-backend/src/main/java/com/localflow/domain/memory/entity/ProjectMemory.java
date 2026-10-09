package com.localflow.domain.memory.entity;

import com.localflow.domain.memory.domain.MemoryType;
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
@Table(name = "project_memories")
public class ProjectMemory {
    @Id @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MemoryType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 10000)
    private String content;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProjectMemory() {
    }

    public static ProjectMemory create(Project project, MemoryType type, String title, String content) {
        ProjectMemory memory = new ProjectMemory();
        memory.id = UUID.randomUUID().toString();
        memory.project = project;
        memory.type = type;
        memory.title = title;
        memory.content = content;
        memory.active = true;
        memory.createdAt = Instant.now();
        memory.updatedAt = memory.createdAt;
        return memory;
    }

    public void update(MemoryType type, String title, String content, boolean active) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public MemoryType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.localflow.domain.memory.entity;

import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
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

    private Boolean globalScope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_memory_scope_tags", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> scopeTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_memory_scope_paths", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "path_pattern", nullable = false, length = 300)
    private Set<String> scopeFilePatterns = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_memory_scope_tasks", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "task_type", nullable = false, length = 40)
    private Set<String> scopeTaskTypes = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProjectMemory() {
    }

    public static ProjectMemory create(Project project, MemoryType type, String title, String content,
                                       boolean globalScope, Collection<String> scopeTags,
                                       Collection<String> scopeFilePatterns,
                                       Collection<String> scopeTaskTypes) {
        ProjectMemory memory = new ProjectMemory();
        memory.id = UUID.randomUUID().toString();
        memory.project = project;
        memory.type = type;
        memory.title = title;
        memory.content = content;
        memory.active = true;
        memory.setScope(globalScope, scopeTags, scopeFilePatterns, scopeTaskTypes);
        memory.createdAt = Instant.now();
        memory.updatedAt = memory.createdAt;
        return memory;
    }

    public static ProjectMemory create(Project project, MemoryType type, String title, String content) {
        return create(project, type, title, content, true, Set.of(), Set.of(), Set.of());
    }

    public void update(MemoryType type, String title, String content, boolean active,
                       boolean globalScope, Collection<String> scopeTags,
                       Collection<String> scopeFilePatterns, Collection<String> scopeTaskTypes) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.active = active;
        setScope(globalScope, scopeTags, scopeFilePatterns, scopeTaskTypes);
        this.updatedAt = Instant.now();
    }

    private void setScope(boolean globalScope, Collection<String> tags,
                          Collection<String> paths, Collection<String> taskTypes) {
        this.globalScope = globalScope;
        this.scopeTags.clear();
        this.scopeFilePatterns.clear();
        this.scopeTaskTypes.clear();
        if (tags != null) this.scopeTags.addAll(tags);
        if (paths != null) this.scopeFilePatterns.addAll(paths);
        if (taskTypes != null) this.scopeTaskTypes.addAll(taskTypes);
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public MemoryType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isActive() { return active; }
    public boolean isGlobalScope() { return globalScope == null || globalScope; }
    public Set<String> getScopeTags() { return Set.copyOf(scopeTags); }
    public Set<String> getScopeFilePatterns() { return Set.copyOf(scopeFilePatterns); }
    public Set<String> getScopeTaskTypes() { return Set.copyOf(scopeTaskTypes); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

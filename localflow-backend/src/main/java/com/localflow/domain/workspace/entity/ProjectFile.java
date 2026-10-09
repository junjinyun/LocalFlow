package com.localflow.domain.workspace.entity;

import com.localflow.domain.project.entity.Project;
import com.localflow.domain.workspace.domain.FileCategory;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
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
@Table(name = "project_files", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_file_path", columnNames = {"project_id", "relative_path"}))
public class ProjectFile {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Column(name = "relative_path", nullable = false, length = 1000)
    private String relativePath;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 30)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileCategory category;

    @Column(length = 30)
    private String language;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_file_tags", joinColumns = @JoinColumn(name = "project_file_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProjectFile() {
    }

    public static ProjectFile create(Project project, String relativePath, String fileName,
                                     String extension, FileCategory category, String language,
                                     long sizeBytes, String sha256) {
        ProjectFile file = new ProjectFile();
        file.id = UUID.randomUUID().toString();
        file.project = project;
        file.relativePath = relativePath;
        file.fileName = fileName;
        file.extension = extension;
        file.category = category;
        file.language = language;
        file.sizeBytes = sizeBytes;
        file.sha256 = sha256;
        file.createdAt = Instant.now();
        file.updatedAt = file.createdAt;
        return file;
    }

    public void refresh(String fileName, String extension, FileCategory category,
                        String language, long sizeBytes, String sha256) {
        this.fileName = fileName;
        this.extension = extension;
        this.category = category;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.updatedAt = Instant.now();
    }

    public void moveTo(String relativePath, String fileName) {
        this.relativePath = relativePath;
        this.fileName = fileName;
        this.updatedAt = Instant.now();
    }

    public void replaceTags(Collection<String> values) {
        tags.clear();
        if (values != null) tags.addAll(values);
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public String getRelativePath() { return relativePath; }
    public String getFileName() { return fileName; }
    public String getExtension() { return extension; }
    public FileCategory getCategory() { return category; }
    public String getLanguage() { return language; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

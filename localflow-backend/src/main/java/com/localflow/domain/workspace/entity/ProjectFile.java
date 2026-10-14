package com.localflow.domain.workspace.entity;

import com.localflow.domain.project.entity.Project;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.domain.FileAnalysisStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileAnalysisStatus analysisStatus;

    @Column(length = 80)
    private String analysisRole;

    @Column(length = 1000)
    private String analysisSummary;

    @Column(length = 500)
    private String analysisMessage;

    private Instant analyzedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_file_tags", joinColumns = @JoinColumn(name = "project_file_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> tags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_file_user_added_tags", joinColumns = @JoinColumn(name = "project_file_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> userAddedTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_file_user_excluded_tags", joinColumns = @JoinColumn(name = "project_file_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> userExcludedTags = new LinkedHashSet<>();

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
        file.analysisStatus = FileAnalysisStatus.PENDING;
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

    public void recordAnalysis(FileAnalysisStatus status, String role, String summary, String message) {
        this.analysisStatus = status;
        this.analysisRole = role;
        this.analysisSummary = summary;
        this.analysisMessage = message;
        this.analyzedAt = Instant.now();
    }

    public void updateTagOverrides(Collection<String> added, Collection<String> excluded) {
        userAddedTags.clear();
        userExcludedTags.clear();
        if (added != null) userAddedTags.addAll(added);
        if (excluded != null) userExcludedTags.addAll(excluded);
        this.updatedAt = Instant.now();
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
    public FileAnalysisStatus getAnalysisStatus() {
        return analysisStatus == null ? FileAnalysisStatus.PENDING : analysisStatus;
    }
    public String getAnalysisRole() { return analysisRole; }
    public String getAnalysisSummary() { return analysisSummary; }
    public String getAnalysisMessage() { return analysisMessage; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Set<String> getTags() {
        Set<String> effective = new LinkedHashSet<>(tags);
        effective.addAll(userAddedTags);
        effective.removeAll(userExcludedTags);
        return Set.copyOf(effective);
    }
    public Set<String> getAutomaticTags() { return Set.copyOf(tags); }
    public Set<String> getUserAddedTags() { return Set.copyOf(userAddedTags); }
    public Set<String> getUserExcludedTags() { return Set.copyOf(userExcludedTags); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

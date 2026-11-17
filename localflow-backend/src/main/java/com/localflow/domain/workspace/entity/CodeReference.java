package com.localflow.domain.workspace.entity;

import com.localflow.domain.workspace.domain.CodeReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "code_references")
public class CodeReference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProjectFile sourceFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CodeReferenceType type;

    @Column(nullable = false, length = 500)
    private String target;

    @Column(nullable = false)
    private int lineNumber;

    protected CodeReference() {
    }

    public CodeReference(ProjectFile sourceFile, CodeReferenceType type,
                         String target, int lineNumber) {
        this.sourceFile = sourceFile;
        this.type = type;
        this.target = target;
        this.lineNumber = lineNumber;
    }

    public Long getId() { return id; }
    public String getSourceFileId() { return sourceFile.getId(); }
    public String getSourceFilePath() { return sourceFile.getRelativePath(); }
    public CodeReferenceType getType() { return type; }
    public String getTarget() { return target; }
    public int getLineNumber() { return lineNumber; }
}

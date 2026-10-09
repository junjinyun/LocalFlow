package com.localflow.domain.workspace.entity;

import com.localflow.domain.workspace.domain.SymbolType;
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
@Table(name = "code_symbols")
public class CodeSymbol {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProjectFile projectFile;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SymbolType type;

    @Column(nullable = false)
    private int lineNumber;

    protected CodeSymbol() {
    }

    public CodeSymbol(ProjectFile projectFile, String name, SymbolType type, int lineNumber) {
        this.projectFile = projectFile;
        this.name = name;
        this.type = type;
        this.lineNumber = lineNumber;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public SymbolType getType() { return type; }
    public int getLineNumber() { return lineNumber; }
}

package com.localflow.domain.workspace.repository;

import com.localflow.domain.workspace.entity.CodeSymbol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSymbolRepository extends JpaRepository<CodeSymbol, Long> {
    List<CodeSymbol> findAllByProjectFile_IdOrderByLineNumber(String projectFileId);
    void deleteAllByProjectFile_Id(String projectFileId);
    void deleteAllByProjectFile_Project_Id(String projectId);
}

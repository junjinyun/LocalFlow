package com.localflow.domain.workspace.repository;

import com.localflow.domain.workspace.entity.CodeSymbol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeSymbolRepository extends JpaRepository<CodeSymbol, Long> {
    List<CodeSymbol> findAllByProjectFile_IdOrderByLineNumber(String projectFileId);
    @Query("select symbol from CodeSymbol symbol join fetch symbol.projectFile file "
            + "where file.project.id = :projectId order by file.relativePath, symbol.lineNumber")
    List<CodeSymbol> findAllByProjectId(@Param("projectId") String projectId);
    void deleteAllByProjectFile_Id(String projectFileId);
    void deleteAllByProjectFile_Project_Id(String projectId);
}

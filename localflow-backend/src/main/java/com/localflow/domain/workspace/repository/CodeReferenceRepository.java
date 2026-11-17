package com.localflow.domain.workspace.repository;

import com.localflow.domain.workspace.entity.CodeReference;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeReferenceRepository extends JpaRepository<CodeReference, Long> {
    List<CodeReference> findAllBySourceFile_IdOrderByLineNumber(String sourceFileId);

    @Query("select reference from CodeReference reference join fetch reference.sourceFile file "
            + "where file.project.id = :projectId order by file.relativePath, reference.lineNumber")
    List<CodeReference> findAllByProjectId(@Param("projectId") String projectId);

    void deleteAllBySourceFile_Id(String sourceFileId);
    void deleteAllBySourceFile_Project_Id(String projectId);
}

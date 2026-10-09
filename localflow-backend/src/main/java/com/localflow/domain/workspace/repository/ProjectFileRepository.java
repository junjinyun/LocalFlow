package com.localflow.domain.workspace.repository;

import com.localflow.domain.workspace.entity.ProjectFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, String> {
    List<ProjectFile> findAllByProject_IdOrderByRelativePath(String projectId);
    Optional<ProjectFile> findByProject_IdAndRelativePath(String projectId, String relativePath);
    long countByProject_Id(String projectId);
    void deleteAllByProject_Id(String projectId);
}

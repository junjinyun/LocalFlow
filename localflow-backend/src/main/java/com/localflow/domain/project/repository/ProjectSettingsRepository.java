package com.localflow.domain.project.repository;

import com.localflow.domain.project.entity.ProjectSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectSettingsRepository extends JpaRepository<ProjectSettings, String> {
    Optional<ProjectSettings> findByProject_Id(String projectId);
    void deleteAllByProject_Id(String projectId);
}

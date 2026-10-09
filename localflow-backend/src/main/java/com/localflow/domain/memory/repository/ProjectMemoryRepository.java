package com.localflow.domain.memory.repository;

import com.localflow.domain.memory.entity.ProjectMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemoryRepository extends JpaRepository<ProjectMemory, String> {
    List<ProjectMemory> findAllByProject_IdOrderByUpdatedAtDesc(String projectId);
    List<ProjectMemory> findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(String projectId);
    void deleteAllByProject_Id(String projectId);
}

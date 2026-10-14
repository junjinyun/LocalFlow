package com.localflow.domain.memory.repository;

import com.localflow.domain.memory.domain.MemoryCandidateStatus;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.entity.ProjectMemoryCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemoryCandidateRepository extends JpaRepository<ProjectMemoryCandidate, String> {
    List<ProjectMemoryCandidate> findAllByProject_IdOrderByCreatedAtDesc(String projectId);
    List<ProjectMemoryCandidate> findAllByProject_IdAndStatusOrderByCreatedAtDesc(
            String projectId, MemoryCandidateStatus status);
    boolean existsByProject_IdAndSourceTypeAndSourceIdAndSourceOrder(
            String projectId, MemoryCandidateSourceType sourceType, String sourceId, int sourceOrder);
    void deleteAllByProject_Id(String projectId);
}

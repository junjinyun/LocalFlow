package com.localflow.domain.memory.service;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.dto.MemoryCandidateExtractionHistoryResponse;
import com.localflow.domain.memory.entity.MemoryCandidateExtractionHistory;
import com.localflow.domain.memory.repository.MemoryCandidateExtractionHistoryRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemoryCandidateExtractionHistoryService {
    private final MemoryCandidateExtractionHistoryRepository repository;
    private final ProjectService projectService;

    public MemoryCandidateExtractionHistoryService(
            MemoryCandidateExtractionHistoryRepository repository,
            ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    public List<MemoryCandidateExtractionHistoryResponse> findRecent(String projectId) {
        projectService.requireProject(projectId);
        return repository.findTop20ByProject_IdOrderByStartedAtDesc(projectId).stream()
                .map(MemoryCandidateExtractionHistoryResponse::from).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String projectId, MemoryCandidateExtractionTrigger triggerType,
                              MemoryCandidateSourceType sourceType, String sourceId,
                              int createdCount, Instant startedAt) {
        Project project = projectService.requireProject(projectId);
        repository.save(MemoryCandidateExtractionHistory.success(project, triggerType,
                sourceType, sourceId, createdCount, startedAt, Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String projectId, MemoryCandidateExtractionTrigger triggerType,
                              MemoryCandidateSourceType sourceType, String sourceId,
                              String errorMessage, Instant startedAt) {
        Project project = projectService.requireProject(projectId);
        repository.save(MemoryCandidateExtractionHistory.failure(project, triggerType,
                sourceType, sourceId, errorMessage, startedAt, Instant.now()));
    }
}

package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.dto.response.AgentRunProgressResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.entity.AgentRunProgressEvent;
import com.localflow.domain.agent.repository.AgentRunProgressEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunProgressService {
    private final AgentRunService runService;
    private final AgentRunProgressEventRepository repository;

    public AgentRunProgressService(AgentRunService runService,
                                   AgentRunProgressEventRepository repository) {
        this.runService = runService;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String projectId, String runId, AgentProgressStage stage, String message) {
        AgentRun run = runService.requireRun(projectId, runId);
        repository.saveAndFlush(AgentRunProgressEvent.create(run, stage, message));
    }

    @Transactional(readOnly = true)
    public List<AgentRunProgressResponse> findAll(String projectId, String runId) {
        runService.requireRun(projectId, runId);
        return repository.findAllByRun_IdOrderByIdAsc(runId).stream()
                .map(AgentRunProgressResponse::from)
                .toList();
    }
}

package com.localflow.domain.agent.repository;

import com.localflow.domain.agent.entity.AgentRunProgressEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunProgressEventRepository extends JpaRepository<AgentRunProgressEvent, Long> {
    List<AgentRunProgressEvent> findAllByRun_IdOrderByIdAsc(String runId);
}

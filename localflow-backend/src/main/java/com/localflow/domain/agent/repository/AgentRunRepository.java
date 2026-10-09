package com.localflow.domain.agent.repository;

import com.localflow.domain.agent.entity.AgentRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    List<AgentRun> findAllByProject_IdOrderByCreatedAtDesc(String projectId);
    void deleteAllByProject_Id(String projectId);
}

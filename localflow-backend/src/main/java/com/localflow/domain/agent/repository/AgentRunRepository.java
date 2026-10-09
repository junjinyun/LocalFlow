package com.localflow.domain.agent.repository;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    List<AgentRun> findAllByProject_IdOrderByCreatedAtDesc(String projectId);

    @EntityGraph(attributePaths = "project")
    @Query("select run from AgentRun run where run.id = :id")
    Optional<AgentRun> findDetailedById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "project")
    @Query("select run from AgentRun run where run.id = :id")
    Optional<AgentRun> findByIdForUpdate(@Param("id") String id);

    @EntityGraph(attributePaths = "project")
    List<AgentRun> findAllByStatusIn(List<AgentRunStatus> statuses);

    void deleteAllByProject_Id(String projectId);
}

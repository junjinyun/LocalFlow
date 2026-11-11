package com.localflow.domain.memory.repository;

import com.localflow.domain.memory.entity.MemoryCandidateExtractionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryCandidateExtractionHistoryRepository
        extends JpaRepository<MemoryCandidateExtractionHistory, String> {
    List<MemoryCandidateExtractionHistory> findTop20ByProject_IdOrderByStartedAtDesc(String projectId);
}

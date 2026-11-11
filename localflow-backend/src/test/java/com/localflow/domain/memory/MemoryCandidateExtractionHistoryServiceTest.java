package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.entity.MemoryCandidateExtractionHistory;
import com.localflow.domain.memory.repository.MemoryCandidateExtractionHistoryRepository;
import com.localflow.domain.memory.service.MemoryCandidateExtractionHistoryService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryCandidateExtractionHistoryServiceTest {
    @Mock MemoryCandidateExtractionHistoryRepository repository;
    @Mock ProjectService projectService;

    private MemoryCandidateExtractionHistoryService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new MemoryCandidateExtractionHistoryService(repository, projectService);
        project = Project.create("memory-history-test", null);
        when(projectService.requireProject(project.getId())).thenReturn(project);
    }

    @Test
    void storesAutomaticExtractionSuccess() {
        service.recordSuccess(project.getId(), MemoryCandidateExtractionTrigger.CHAT_MESSAGE,
                MemoryCandidateSourceType.CHAT_MESSAGE, "message-id", 2, Instant.now());

        ArgumentCaptor<MemoryCandidateExtractionHistory> captor =
                ArgumentCaptor.forClass(MemoryCandidateExtractionHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedCount()).isEqualTo(2);
        assertThat(captor.getValue().getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    void returnsRecentExtractionHistory() {
        MemoryCandidateExtractionHistory history = MemoryCandidateExtractionHistory.success(
                project, MemoryCandidateExtractionTrigger.MANUAL, null, null,
                1, Instant.now(), Instant.now());
        when(repository.findTop20ByProject_IdOrderByStartedAtDesc(project.getId()))
                .thenReturn(List.of(history));

        var result = service.findRecent(project.getId());

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.triggerType()).isEqualTo(MemoryCandidateExtractionTrigger.MANUAL);
            assertThat(value.createdCount()).isEqualTo(1);
        });
    }
}

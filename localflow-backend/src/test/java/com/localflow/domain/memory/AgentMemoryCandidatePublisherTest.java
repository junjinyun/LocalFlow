package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.service.AgentMemoryCandidatePublisher;
import com.localflow.domain.memory.service.MemoryCandidateExtractionDispatchService;
import com.localflow.domain.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentMemoryCandidatePublisherTest {
    @Mock MemoryCandidateExtractionDispatchService dispatchService;

    private AgentMemoryCandidatePublisher publisher;
    private Project project;

    @BeforeEach
    void setUp() {
        publisher = new AgentMemoryCandidatePublisher(dispatchService);
        project = Project.create("agent-memory-test", null);
    }

    @Test
    void requestsExtractionForCompletedRunResult() {
        AgentRun run = AgentRun.draft(project, "요청", ExecutionMode.BALANCED, null, null);
        run.complete("{}", "{}", "앞으로 테스트를 항상 실행한다.",
                "gemini-2.5-flash", 10, 5);

        publisher.request(run);

        verify(dispatchService).request(project.getId(), MemoryCandidateSourceType.AGENT_RUN,
                run.getId(), run.getResultMessage());
    }

    @Test
    void ignoresIncompleteRun() {
        AgentRun run = AgentRun.draft(project, "요청", ExecutionMode.BALANCED, null, null);

        publisher.request(run);

        verify(dispatchService, never()).request(project.getId(), MemoryCandidateSourceType.AGENT_RUN,
                run.getId(), run.getResultMessage());
    }

    @Test
    void isolatesDispatchFailureFromCompletedRun() {
        AgentRun run = AgentRun.draft(project, "요청", ExecutionMode.BALANCED, null, null);
        run.complete("{}", "{}", "앞으로 테스트를 항상 실행한다.",
                "gemini-2.5-flash", 10, 5);
        doThrow(new IllegalStateException("dispatch failed")).when(dispatchService)
                .request(project.getId(), MemoryCandidateSourceType.AGENT_RUN,
                        run.getId(), run.getResultMessage());

        var publisher = new AgentMemoryCandidatePublisher(dispatchService);

        assertThatCode(() -> publisher.request(run)).doesNotThrowAnyException();
    }
}

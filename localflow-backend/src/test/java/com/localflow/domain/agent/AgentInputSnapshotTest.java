package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentInputSnapshot;
import com.localflow.domain.agent.domain.AgentInputSnapshot.MemoryReference;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInputSnapshotTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesStoredGenerationInputThroughRunDetailResponse() throws Exception {
        Project project = Project.create("sample", null);
        AgentRun run = AgentRun.draft(project, "로그인 기능 설명", ExecutionMode.BALANCED,
                AiProviderType.OLLAMA, "qwen3.5:4b-q4_K_M");
        String snapshot = objectMapper.writeValueAsString(new AgentInputSnapshot(
                List.of("src/AuthService.java"), List.of(new MemoryReference("m1", "인증 규칙")),
                "system prompt", "user prompt"));
        run.recordInputSnapshot(snapshot);

        AgentRunResponse response = AgentRunResponse.from(run);

        assertThat(response.inputSnapshotJson()).isEqualTo(snapshot);
    }

    @Test
    void accumulatesTaskInputsAndReadsLegacySnapshotWithoutHistory() throws Exception {
        AgentInputSnapshot legacy = objectMapper.readValue("""
                {"contextFiles":["src/Legacy.java"],"memories":[],
                 "systemPrompt":"system","userPrompt":"legacy","budget":null,"fileSelections":[]}
                """, AgentInputSnapshot.class);
        AgentInputSnapshot first = new AgentInputSnapshot(List.of("src/First.java"), List.of(),
                "system", "first task");
        AgentInputSnapshot second = new AgentInputSnapshot(List.of("src/Second.java"), List.of(),
                "system", "second task");

        AgentInputSnapshot accumulated = legacy
                .appendTaskSnapshot("task-1", 1, "첫 작업", first)
                .appendTaskSnapshot("task-2", 2, "둘째 작업", second);

        assertThat(legacy.taskSnapshots()).isEmpty();
        assertThat(accumulated.contextFiles()).containsExactly("src/Second.java");
        assertThat(accumulated.taskSnapshots()).extracting(AgentInputSnapshot.TaskInputSnapshot::taskId)
                .containsExactly("task-1", "task-2");
        assertThat(accumulated.taskSnapshots().get(0).input().contextFiles())
                .containsExactly("src/First.java");
    }
}

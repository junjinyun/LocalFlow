package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentInputSnapshot;
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
                List.of("src/AuthService.java"), "system prompt", "user prompt"));
        run.recordInputSnapshot(snapshot);

        AgentRunResponse response = AgentRunResponse.from(run);

        assertThat(response.inputSnapshotJson()).isEqualTo(snapshot);
    }
}

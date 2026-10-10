package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.entity.AgentRunProgressEvent;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import org.junit.jupiter.api.Test;

class AgentRunProgressEventTest {
    @Test
    void truncatesLongFailureMessageToDatabaseColumnLength() {
        AgentRun run = AgentRun.draft(Project.create("sample", null), "test",
                ExecutionMode.BALANCED, AiProviderType.OLLAMA, "qwen2.5-coder:3b");

        AgentRunProgressEvent event = AgentRunProgressEvent.create(
                run, AgentProgressStage.FAILED, "오".repeat(700));

        assertThat(event.getMessage()).hasSize(500).endsWith("…");
    }
}

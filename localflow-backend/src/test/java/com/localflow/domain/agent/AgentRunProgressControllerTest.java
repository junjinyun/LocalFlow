package com.localflow.domain.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AgentRunProgressControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AgentRunService runService;

    @Autowired
    private AgentRunProgressService progressService;

    @Test
    void returnsAccumulatedProgressEventsInCreationOrder() throws Exception {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("progress-project", "진행 이벤트 테스트"));
        try {
            AgentRunResponse run = runService.createDraft(project.id(),
                    new AgentRunCreateRequest("로그인 기능을 추가해줘", ExecutionMode.BALANCED, null));

            progressService.append(project.id(), run.runId(), AgentProgressStage.PREPARING,
                    "요청을 준비하는 중입니다.");
            progressService.append(project.id(), run.runId(), AgentProgressStage.DECOMPOSING,
                    "요청을 작업 단위로 분해하는 중입니다.");

            mockMvc.perform(get("/api/projects/{projectId}/agent-runs/{runId}/progress",
                            project.id(), run.runId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].stage").value("PREPARING"))
                    .andExpect(jsonPath("$.data[1].stage").value("DECOMPOSING"));
        } finally {
            projectService.delete(project.id());
        }
    }
}

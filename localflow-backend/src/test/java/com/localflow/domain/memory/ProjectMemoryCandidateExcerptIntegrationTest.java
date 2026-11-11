package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.dto.MemoryCandidateResponse;
import com.localflow.domain.memory.repository.ProjectMemoryCandidateRepository;
import com.localflow.domain.memory.service.ProjectMemoryCandidateService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectMemoryCandidateExcerptIntegrationTest {
    @Autowired ProjectMemoryCandidateService candidateService;
    @Autowired ProjectMemoryCandidateRepository candidateRepository;
    @Autowired ProjectRepository projectRepository;

    @Test
    void storesCandidateFromLongAgentRunResultWithinDatabaseLengthLimit() {
        Project project = projectRepository.save(Project.create("long-agent-result", null));
        String longResult = "앞으로 모든 변경은 테스트한다. " + "상세 실행 결과 ".repeat(100);

        List<MemoryCandidateResponse> result = candidateService.extractSource(project.getId(),
                MemoryCandidateSourceType.AGENT_RUN, "long-agent-run-id", longResult);
        candidateRepository.flush();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceType()).isEqualTo(MemoryCandidateSourceType.AGENT_RUN);
        assertThat(result.get(0).sourceExcerpt()).hasSize(500).endsWith("…");
        assertThat(candidateRepository.findById(result.get(0).id())).isPresent();
    }
}

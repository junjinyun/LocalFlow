package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.service.MemoryCandidateExtractionListener;
import com.localflow.domain.memory.service.ProjectMemoryCandidateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryCandidateExtractionListenerTest {
    @Mock ProjectMemoryCandidateService candidateService;

    @Test
    void isolatesExtractionFailureFromOriginalOperation() {
        var event = new MemoryCandidateExtractionRequestedEvent(
                "project-id", MemoryCandidateSourceType.CHAT_MESSAGE,
                "message-id", "앞으로 테스트를 실행한다.");
        doThrow(new IllegalStateException("extraction failed"))
                .when(candidateService).extractSource(event.projectId(), event.sourceType(),
                        event.sourceId(), event.content());

        var listener = new MemoryCandidateExtractionListener(candidateService);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }
}

package com.localflow.domain.memory.service;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.domain.MemoryCandidateStatus;
import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.dto.MemoryCandidateApproveRequest;
import com.localflow.domain.memory.dto.MemoryCandidateResponse;
import com.localflow.domain.memory.dto.ProjectMemoryCreateRequest;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.entity.ProjectMemory;
import com.localflow.domain.memory.entity.ProjectMemoryCandidate;
import com.localflow.domain.memory.repository.ProjectMemoryCandidateRepository;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectMemoryCandidateService {
    private static final Pattern CANDIDATE_MARKER = Pattern.compile(
            "(앞으로|항상|기억|규칙|원칙|결정|추후|나중에|남은\s*작업|미완료|해야\s*한다|하도록)");
    private static final int MAX_CANDIDATES_PER_SOURCE = 5;

    private final ProjectMemoryCandidateRepository candidateRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ChatMessageRepository chatRepository;
    private final AgentRunRepository runRepository;
    private final ProjectMemoryService memoryService;
    private final ProjectService projectService;

    public ProjectMemoryCandidateService(ProjectMemoryCandidateRepository candidateRepository,
                                         ProjectMemoryRepository memoryRepository,
                                         ChatMessageRepository chatRepository,
                                         AgentRunRepository runRepository,
                                         ProjectMemoryService memoryService,
                                         ProjectService projectService) {
        this.candidateRepository = candidateRepository;
        this.memoryRepository = memoryRepository;
        this.chatRepository = chatRepository;
        this.runRepository = runRepository;
        this.memoryService = memoryService;
        this.projectService = projectService;
    }

    public List<MemoryCandidateResponse> findAll(String projectId, boolean pendingOnly) {
        projectService.requireProject(projectId);
        List<ProjectMemoryCandidate> candidates = pendingOnly
                ? candidateRepository.findAllByProject_IdAndStatusOrderByCreatedAtDesc(
                        projectId, MemoryCandidateStatus.PENDING)
                : candidateRepository.findAllByProject_IdOrderByCreatedAtDesc(projectId);
        return candidates.stream().map(this::response).toList();
    }

    @Transactional
    public List<MemoryCandidateResponse> extract(String projectId) {
        Project project = projectService.requireProject(projectId);
        List<ProjectMemory> memories = memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(projectId);
        List<ProjectMemoryCandidate> created = new ArrayList<>();

        for (ChatMessage message : chatRepository.findAllByProject_IdOrderByCreatedAtAsc(projectId)) {
            if (message.getType() == ChatMessageType.PROMPT || message.getType() == ChatMessageType.NOTE
                    || message.getType() == ChatMessageType.HANDOFF) {
                created.addAll(extractFrom(project, MemoryCandidateSourceType.CHAT_MESSAGE,
                        message.getId(), message.getContent(), memories));
            }
        }
        for (AgentRun run : runRepository.findAllByProject_IdOrderByCreatedAtDesc(projectId)) {
            if ((run.getStatus() == AgentRunStatus.COMPLETED
                    || run.getStatus() == AgentRunStatus.PARTIALLY_COMPLETED)
                    && run.getResultMessage() != null) {
                created.addAll(extractFrom(project, MemoryCandidateSourceType.AGENT_RUN,
                        run.getId(), run.getResultMessage(), memories));
            }
        }
        return candidateRepository.saveAll(created).stream().map(this::response).toList();
    }

    @Transactional
    public ProjectMemoryResponse approve(String projectId, String candidateId,
                                         MemoryCandidateApproveRequest request) {
        ProjectMemoryCandidate candidate = requireCandidate(projectId, candidateId);
        requirePending(candidate);
        ProjectMemoryResponse memory = memoryService.create(projectId, new ProjectMemoryCreateRequest(
                request.type(), request.title(), request.content(), request.globalScope(),
                request.scopeTags(), request.scopeFilePatterns(), request.scopeTaskTypes()));
        candidate.approve(memory.id());
        return memory;
    }

    @Transactional
    public MemoryCandidateResponse reject(String projectId, String candidateId) {
        ProjectMemoryCandidate candidate = requireCandidate(projectId, candidateId);
        requirePending(candidate);
        candidate.reject();
        return response(candidate);
    }

    private List<ProjectMemoryCandidate> extractFrom(Project project, MemoryCandidateSourceType sourceType,
                                                     String sourceId, String content,
                                                     List<ProjectMemory> memories) {
        List<ProjectMemoryCandidate> candidates = new ArrayList<>();
        String[] parts = content.replace("\r", "").split("(?<=[.!?。！？])\\s+|\\n+");
        int candidateOrder = 0;
        for (String raw : parts) {
            String sentence = clean(raw);
            if (sentence.isBlank() || !CANDIDATE_MARKER.matcher(sentence).find()) continue;
            int sourceOrder = candidateOrder++;
            if (sourceOrder >= MAX_CANDIDATES_PER_SOURCE) break;
            if (candidateRepository.existsByProject_IdAndSourceTypeAndSourceIdAndSourceOrder(
                    project.getId(), sourceType, sourceId, sourceOrder)) continue;

            MemoryType type = inferType(sentence);
            String title = title(sentence, type);
            String duplicateId = possibleDuplicate(memories, title, sentence)
                    .map(ProjectMemory::getId).orElse(null);
            candidates.add(ProjectMemoryCandidate.create(project, sourceType, sourceId, sourceOrder,
                    excerpt(content), type, title, sentence, true, Set.of(), Set.of(), Set.of(), duplicateId));
        }
        return candidates;
    }

    private MemoryType inferType(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(남은\\s*작업|미완료|추후|나중에|해야\\s*한다).*$")) {
            return MemoryType.OPEN_TASK;
        }
        if (lower.matches(".*(결정|채택|사용하기로).*$")) return MemoryType.IMPLEMENTATION_DECISION;
        if (lower.matches(".*(오류|실패|예외).*$")) return MemoryType.ERROR_CONTEXT;
        if (lower.matches(".*(항상|규칙|원칙|앞으로).*$")) return MemoryType.PROJECT_RULE;
        return MemoryType.NOTE;
    }

    private Optional<ProjectMemory> possibleDuplicate(List<ProjectMemory> memories, String title, String content) {
        String normalizedTitle = normalize(title);
        String normalizedContent = normalize(content);
        return memories.stream().filter(memory -> {
            String existingTitle = normalize(memory.getTitle());
            String existingContent = normalize(memory.getContent());
            return existingTitle.equals(normalizedTitle) || existingContent.equals(normalizedContent)
                    || (normalizedContent.length() >= 20
                    && (existingContent.contains(normalizedContent) || normalizedContent.contains(existingContent)));
        }).findFirst();
    }

    private MemoryCandidateResponse response(ProjectMemoryCandidate candidate) {
        String duplicateTitle = candidate.getPossibleDuplicateMemoryId() == null ? null
                : memoryRepository.findById(candidate.getPossibleDuplicateMemoryId())
                .map(ProjectMemory::getTitle).orElse(null);
        return MemoryCandidateResponse.from(candidate, duplicateTitle);
    }

    private ProjectMemoryCandidate requireCandidate(String projectId, String candidateId) {
        ProjectMemoryCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMORY_CANDIDATE_NOT_FOUND));
        if (!candidate.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.MEMORY_CANDIDATE_NOT_FOUND);
        }
        return candidate;
    }

    private void requirePending(ProjectMemoryCandidate candidate) {
        if (candidate.getStatus() != MemoryCandidateStatus.PENDING) {
            throw new CustomException(ErrorCode.MEMORY_CANDIDATE_ALREADY_REVIEWED);
        }
    }

    private String clean(String value) {
        return value.replaceAll("[`*_>#-]+", " ").replaceAll("\\s+", " ").strip();
    }

    private String title(String content, MemoryType type) {
        String prefix = switch (type) {
            case PROJECT_RULE -> "프로젝트 규칙";
            case IMPLEMENTATION_DECISION -> "구현 결정";
            case OPEN_TASK -> "남은 작업";
            case ERROR_CONTEXT -> "오류 맥락";
            case NOTE -> "참고 메모";
        };
        String shortened = content.length() > 90 ? content.substring(0, 90).strip() + "…" : content;
        return (prefix + ": " + shortened).substring(0, Math.min(150, prefix.length() + 2 + shortened.length()));
    }

    private String excerpt(String content) {
        String cleaned = clean(content);
        return cleaned.length() > 500 ? cleaned.substring(0, 500) + "…" : cleaned;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }
}

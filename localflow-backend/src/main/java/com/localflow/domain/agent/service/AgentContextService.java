package com.localflow.domain.agent.service;

import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.entity.ProjectMemory;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.memory.service.ProjectMemoryScopeMatcher;
import com.localflow.domain.agent.domain.AgentInputSnapshot.MemoryReference;
import com.localflow.domain.agent.domain.AgentInputSnapshot.FileSelection;
import com.localflow.domain.agent.domain.AgentInputSnapshot.ExcludedFile;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.TaskScope;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentContextService {
    private static final int MAX_CONTEXT_FILES = 12;
    private final ProjectFileRepository fileRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ChatMessageRepository chatRepository;
    private final WorkspaceStorageService storageService;
    private final AiProviderProperties properties;
    private final ProjectMemoryScopeMatcher memoryScopeMatcher;

    public AgentContextService(ProjectFileRepository fileRepository,
                               ProjectMemoryRepository memoryRepository,
                               ChatMessageRepository chatRepository,
                               WorkspaceStorageService storageService,
                               AiProviderProperties properties,
                               ProjectMemoryScopeMatcher memoryScopeMatcher) {
        this.fileRepository = fileRepository;
        this.memoryRepository = memoryRepository;
        this.chatRepository = chatRepository;
        this.storageService = storageService;
        this.properties = properties;
        this.memoryScopeMatcher = memoryScopeMatcher;
    }

    public List<ProjectFile> files(String projectId) {
        return fileRepository.findAllByProject_IdOrderByRelativePath(projectId);
    }

    public String buildDecompositionContext(Project project) {
        List<ProjectFile> files = files(project.getId());
        StringBuilder context = new StringBuilder();
        append(context, "프로젝트", project.getName() + "\n설명: " + nullable(project.getDescription()));
        append(context, "전체 적용 프로젝트 기억", globalMemories(project.getId()));
        append(context, "최근 대화", recentMessages(project.getId()));
        append(context, "사용 가능한 태그", files.stream().flatMap(file -> file.getTags().stream())
                .distinct().sorted().toList().toString());
        append(context, "전체 파일 인덱스", files.stream()
                .map(file -> file.getRelativePath() + " tags=" + file.getTags()).toList().toString());
        return context.substring(0, Math.min(context.length(), properties.contextMaxChars()));
    }

    public ContextSnapshot buildSnapshot(Project project, String prompt,
                                         DecisionResult decision, boolean includeContents) {
        return buildSnapshot(project, prompt, decision, includeContents, properties.contextMaxChars());
    }

    public ContextSnapshot buildSnapshot(Project project, String prompt,
                                         DecisionResult decision, boolean includeContents,
                                         int maxContextChars) {
        List<ProjectFile> files = files(project.getId());
        StringBuilder context = new StringBuilder();
        List<String> contextFiles = new ArrayList<>();
        List<String> truncatedFiles = new ArrayList<>();
        List<ExcludedFile> excludedFiles = new ArrayList<>();
        List<ScoredFile> selectedFiles = includeContents ? select(files, prompt, decision) : List.of();
        List<String> selectedPaths = new ArrayList<>(selectedFiles.stream()
                .map(value -> value.file().getRelativePath()).toList());
        if (decision.targetPath() != null && !selectedPaths.contains(decision.targetPath())) {
            selectedPaths.add(decision.targetPath());
        }
        Set<String> selectedTags = new LinkedHashSet<>(decision.selectedTags());
        selectedFiles.forEach(value -> selectedTags.addAll(value.file().getTags()));
        List<ProjectMemory> matchedMemories = matchedMemories(project.getId(), decision.actionType(),
                selectedTags, selectedPaths);
        append(context, "프로젝트", project.getName() + "\n설명: " + nullable(project.getDescription()));
        append(context, "관련 프로젝트 기억", memoryText(matchedMemories));
        append(context, "최근 대화", recentMessages(project.getId()));
        append(context, "분해된 작업 단위", taskDescription(decision.taskScopes()));
        append(context, "선택된 파일 태그", decision.selectedTags().toString());
        append(context, "전체 파일 인덱스", files.stream()
                .map(file -> file.getRelativePath() + " tags=" + file.getTags()).toList().toString());
        int metadataLimit = Math.max(1_000, maxContextChars * 2 / 5);
        if (context.length() > metadataLimit) context.setLength(metadataLimit);
        Set<String> selectedPathSet = selectedFiles.stream()
                .map(value -> value.file().getRelativePath()).collect(java.util.stream.Collectors.toSet());
        files.stream().filter(file -> file.getCategory() != FileCategory.DOCUMENT)
                .filter(file -> !selectedPathSet.contains(file.getRelativePath()))
                .forEach(file -> excludedFiles.add(new ExcludedFile(
                        file.getRelativePath(), "관련도 우선순위에서 컨텍스트 대상 밖으로 제외되었습니다.")));
        if (includeContents) {
            for (ScoredFile selected : selectedFiles) {
                ProjectFile file = selected.file();
                if (context.length() >= maxContextChars) {
                    excludedFiles.add(new ExcludedFile(file.getRelativePath(), "컨텍스트 예산이 소진되었습니다."));
                    continue;
                }
                String content = storageService.readText(project.getId(), file.getRelativePath());
                int remaining = maxContextChars - context.length();
                if (remaining <= 100) {
                    excludedFiles.add(new ExcludedFile(file.getRelativePath(), "파일 본문을 넣을 예산이 부족합니다."));
                    continue;
                }
                if (content.length() > remaining) {
                    content = content.substring(0, Math.max(0, remaining - 8)) + "\n[잘림]";
                    truncatedFiles.add(file.getRelativePath());
                }
                append(context, "파일: " + file.getRelativePath(), content);
                contextFiles.add(file.getRelativePath());
            }
        }
        String value = context.substring(0, Math.min(context.length(), maxContextChars));
        return new ContextSnapshot(value, contextFiles, matchedMemories.stream()
                .map(memory -> new MemoryReference(memory.getId(), memory.getTitle())).toList(),
                truncatedFiles, excludedFiles, maxContextChars, value.length(),
                selectedFiles.stream().map(ScoredFile::selection).toList());
    }

    public String build(Project project, String prompt, DecisionResult decision, boolean includeContents) {
        return buildSnapshot(project, prompt, decision, includeContents).content();
    }

    private List<ScoredFile> select(List<ProjectFile> files, String prompt, DecisionResult decision) {
        List<ProjectFile> readable = files.stream()
                .filter(file -> file.getCategory() != FileCategory.DOCUMENT).toList();
        List<TaskScope> scopes = decision.taskScopes().isEmpty()
                ? List.of(new TaskScope(prompt, decision.selectedTags())) : decision.taskScopes();
        return readable.stream().map(file -> rank(file, prompt, decision, scopes))
                .sorted(Comparator.comparingInt(ScoredFile::score).reversed()
                        .thenComparing(value -> value.file().getRelativePath()))
                .limit(MAX_CONTEXT_FILES).toList();
    }

    private ScoredFile rank(ProjectFile file, String prompt, DecisionResult decision, List<TaskScope> scopes) {
        ScoreDetail best = score(file, tokens(prompt), decision.targetPath(), decision.selectedTags());
        for (TaskScope scope : scopes) {
            ScoreDetail candidate = score(file, tokens(scope.instruction()), decision.targetPath(), scope.tags());
            if (candidate.score() > best.score()) best = candidate;
        }
        List<String> reasons = best.reasons().isEmpty()
                ? List.of("파일 경로 기준의 안정적인 보조 순위") : best.reasons();
        return new ScoredFile(file, best.score(),
                new FileSelection(file.getRelativePath(), best.score(), reasons));
    }

    private ScoreDetail score(ProjectFile file, Set<String> terms, String targetPath, List<String> selectedTags) {
        String path = file.getRelativePath().toLowerCase(Locale.ROOT);
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (file.getRelativePath().equals(targetPath)) {
            score += 10_000;
            reasons.add("AI가 지정한 대상 파일과 일치");
        }
        List<String> matchedTags = selectedTags.stream().filter(file.getTags()::contains).sorted().toList();
        if (!matchedTags.isEmpty()) {
            score += matchedTags.size() * 100;
            reasons.add("선택 태그 일치: " + String.join(", ", matchedTags));
        }
        List<String> matchedTerms = terms.stream().filter(path::contains).sorted().toList();
        if (!matchedTerms.isEmpty()) {
            score += matchedTerms.size() * 10;
            reasons.add("요청 키워드가 경로에 포함: " + String.join(", ", matchedTerms));
        }
        if (path.endsWith("readme.md")) {
            score += 2;
            reasons.add("프로젝트 안내 파일");
        }
        return new ScoreDetail(score, List.copyOf(reasons));
    }

    private String taskDescription(List<TaskScope> scopes) {
        if (scopes.isEmpty()) return "(단일 작업)";
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < scopes.size(); index++) {
            TaskScope scope = scopes.get(index);
            lines.add((index + 1) + ". " + scope.instruction() + " tags=" + scope.tags());
        }
        return String.join("\n", lines);
    }

    private Set<String> tokens(String prompt) {
        Set<String> result = new HashSet<>();
        for (String value : prompt.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_.-]+")) {
            if (value.length() >= 2) result.add(value);
        }
        return result;
    }

    private String globalMemories(String projectId) {
        List<ProjectMemory> values = memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(projectId);
        return memoryText(values.stream().filter(ProjectMemory::isGlobalScope).toList());
    }

    private List<ProjectMemory> matchedMemories(String projectId, String taskType,
                                                Set<String> tags, List<String> files) {
        return memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(projectId).stream()
                .filter(memory -> memoryScopeMatcher.matches(memory, taskType, tags, files))
                .limit(20).toList();
    }

    private String memoryText(List<ProjectMemory> values) {
        if (values.isEmpty()) return "(없음)";
        List<String> lines = new ArrayList<>();
        values.stream().limit(20).forEach(memory ->
                lines.add("[" + memory.getType() + "] " + memory.getTitle() + ": " + memory.getContent()));
        return String.join("\n", lines);
    }

    private String recentMessages(String projectId) {
        List<ChatMessage> values = chatRepository.findAllByProject_IdOrderByCreatedAtAsc(projectId);
        int start = Math.max(0, values.size() - 12);
        List<String> lines = new ArrayList<>();
        values.subList(start, values.size()).forEach(message ->
                lines.add("[" + message.getType() + "] " + abbreviate(message.getContent(), 1200)));
        return lines.isEmpty() ? "(없음)" : String.join("\n", lines);
    }

    private void append(StringBuilder target, String title, String value) {
        target.append("\n## ").append(title).append('\n').append(value).append('\n');
    }

    private String nullable(String value) { return value == null || value.isBlank() ? "(없음)" : value; }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }

    public record ContextSnapshot(String content, List<String> files, List<MemoryReference> memories,
                                  List<String> truncatedFiles, List<ExcludedFile> excludedFiles,
                                  int maxChars, int usedChars, List<FileSelection> fileSelections) {
        public ContextSnapshot {
            files = files == null ? List.of() : List.copyOf(files);
            memories = memories == null ? List.of() : List.copyOf(memories);
            truncatedFiles = truncatedFiles == null ? List.of() : List.copyOf(truncatedFiles);
            excludedFiles = excludedFiles == null ? List.of() : List.copyOf(excludedFiles);
            fileSelections = fileSelections == null ? List.of() : List.copyOf(fileSelections);
        }
    }

    private record ScoreDetail(int score, List<String> reasons) { }
    private record ScoredFile(ProjectFile file, int score, FileSelection selection) { }
}

package com.localflow.domain.agent.service;

import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.entity.ProjectMemory;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
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

    public AgentContextService(ProjectFileRepository fileRepository,
                               ProjectMemoryRepository memoryRepository,
                               ChatMessageRepository chatRepository,
                               WorkspaceStorageService storageService,
                               AiProviderProperties properties) {
        this.fileRepository = fileRepository;
        this.memoryRepository = memoryRepository;
        this.chatRepository = chatRepository;
        this.storageService = storageService;
        this.properties = properties;
    }

    public List<ProjectFile> files(String projectId) {
        return fileRepository.findAllByProject_IdOrderByRelativePath(projectId);
    }

    public String buildDecompositionContext(Project project) {
        List<ProjectFile> files = files(project.getId());
        StringBuilder context = new StringBuilder();
        append(context, "프로젝트", project.getName() + "\n설명: " + nullable(project.getDescription()));
        append(context, "활성 프로젝트 기억", memories(project.getId()));
        append(context, "최근 대화", recentMessages(project.getId()));
        append(context, "사용 가능한 태그", files.stream().flatMap(file -> file.getTags().stream())
                .distinct().sorted().toList().toString());
        append(context, "전체 파일 인덱스", files.stream()
                .map(file -> file.getRelativePath() + " tags=" + file.getTags()).toList().toString());
        return context.substring(0, Math.min(context.length(), properties.contextMaxChars()));
    }

    public String build(Project project, String prompt, DecisionResult decision, boolean includeContents) {
        List<ProjectFile> files = files(project.getId());
        StringBuilder context = new StringBuilder();
        append(context, "프로젝트", project.getName() + "\n설명: " + nullable(project.getDescription()));
        append(context, "활성 프로젝트 기억", memories(project.getId()));
        append(context, "최근 대화", recentMessages(project.getId()));
        append(context, "분해된 작업 단위", taskDescription(decision.taskScopes()));
        append(context, "선택된 파일 태그", decision.selectedTags().toString());
        append(context, "전체 파일 인덱스", files.stream()
                .map(file -> file.getRelativePath() + " tags=" + file.getTags()).toList().toString());
        if (includeContents) {
            for (ProjectFile file : select(files, prompt, decision)) {
                if (context.length() >= properties.contextMaxChars()) break;
                String content = storageService.readText(project.getId(), file.getRelativePath());
                int remaining = properties.contextMaxChars() - context.length();
                if (remaining <= 100) break;
                if (content.length() > remaining) content = content.substring(0, remaining) + "\n[잘림]";
                append(context, "파일: " + file.getRelativePath(), content);
            }
        }
        return context.substring(0, Math.min(context.length(), properties.contextMaxChars()));
    }

    private List<ProjectFile> select(List<ProjectFile> files, String prompt, DecisionResult decision) {
        List<ProjectFile> readable = files.stream()
                .filter(file -> file.getCategory() != FileCategory.DOCUMENT).toList();
        Set<ProjectFile> selected = new LinkedHashSet<>();
        List<TaskScope> scopes = decision.taskScopes().isEmpty()
                ? List.of(new TaskScope(prompt, decision.selectedTags())) : decision.taskScopes();
        int perTask = Math.max(2, MAX_CONTEXT_FILES / scopes.size());
        for (TaskScope scope : scopes) {
            readable.stream()
                    .sorted(Comparator.comparingInt((ProjectFile file) -> score(
                                    file, tokens(scope.instruction()), decision.targetPath(), scope.tags())).reversed()
                            .thenComparing(ProjectFile::getRelativePath))
                    .limit(perTask)
                    .forEach(file -> {
                        if (selected.size() < MAX_CONTEXT_FILES) selected.add(file);
                    });
        }
        if (selected.size() < MAX_CONTEXT_FILES) {
            readable.stream()
                    .sorted(Comparator.comparingInt((ProjectFile file) -> score(
                                    file, tokens(prompt), decision.targetPath(), decision.selectedTags())).reversed()
                            .thenComparing(ProjectFile::getRelativePath))
                    .forEach(file -> {
                        if (selected.size() < MAX_CONTEXT_FILES) selected.add(file);
                    });
        }
        return List.copyOf(selected);
    }

    private int score(ProjectFile file, Set<String> terms, String targetPath, List<String> selectedTags) {
        if (file.getRelativePath().equals(targetPath)) return 10_000;
        String path = file.getRelativePath().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String tag : selectedTags) if (file.getTags().contains(tag)) score += 100;
        for (String term : terms) if (path.contains(term)) score += 10;
        if (path.endsWith("readme.md")) score += 2;
        return score;
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

    private String memories(String projectId) {
        List<ProjectMemory> values = memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(projectId);
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
}

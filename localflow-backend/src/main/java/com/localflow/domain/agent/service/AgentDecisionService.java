package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.TaskDecomposition;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionRequest;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.TaskScope;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.DecisionProvider;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class AgentDecisionService {
    private static final Set<String> ACTIONS = Set.of(
            "ANSWER_ONLY", "INSPECT_FILES", "MODIFY_FILES", "CREATE_FILES", "DELETE_FILES", "MIXED");
    private static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH");
    private final List<DecisionProvider> providers;
    private final PromptTaskDecomposer decomposer;
    private final com.localflow.domain.provider.service.ProviderRetryExecutor retryExecutor;

    public AgentDecisionService(List<DecisionProvider> providers, PromptTaskDecomposer decomposer,
                                com.localflow.domain.provider.service.ProviderRetryExecutor retryExecutor) {
        this.providers = providers;
        this.decomposer = decomposer;
        this.retryExecutor = retryExecutor;
    }

    public DecisionResult decide(String prompt, List<ProjectFile> projectFiles, boolean externalAllowed,
                                 TaskDecomposition decomposition) {
        return decide(prompt, projectFiles, externalAllowed, decomposition, () -> { }, notice -> { });
    }

    public DecisionResult decide(String prompt, List<ProjectFile> projectFiles, boolean externalAllowed,
                                 TaskDecomposition decomposition, Runnable checkpoint,
                                 Consumer<com.localflow.domain.provider.service.ProviderRetryExecutor.RetryNotice> retryListener) {
        List<String> candidateFiles = projectFiles.stream().map(ProjectFile::getRelativePath).toList();
        List<String> candidateTags = projectFiles.stream().flatMap(file -> file.getTags().stream())
                .distinct().sorted().toList();
        List<TaskScope> proposedScopes = decomposition.tasks().stream().map(task -> {
            Set<String> tags = new LinkedHashSet<>(task.suggestedTags());
            tags.addAll(decomposer.tagsFor(task.instruction(), candidateTags));
            return new TaskScope(task.instruction(), List.copyOf(tags));
        }).toList();
        if (externalAllowed) {
            DecisionProvider jev = providers.stream()
                    .filter(provider -> provider.providerType() == AiProviderType.JEV && provider.available())
                    .findFirst().orElse(null);
            if (jev != null) {
                try {
                    DecisionResult result = retryExecutor.execute(() -> jev.decide(new DecisionRequest(
                                    state(prompt, projectFiles, proposedScopes), candidateFiles, candidateTags,
                                    proposedScopes.stream().map(TaskScope::instruction).toList())),
                            checkpoint, retryListener);
                    validate(result, candidateFiles, candidateTags, proposedScopes);
                    return mergeScopes(result, proposedScopes);
                } catch (RuntimeException exception) {
                    ProviderErrorCode code = exception instanceof ProviderCallException provider
                            ? provider.errorCode() : ProviderErrorCode.INVALID_RESPONSE;
                    return heuristic(prompt, candidateFiles, proposedScopes,
                            fallbackReason(exception), code);
                }
            }
            return heuristic(prompt, candidateFiles, proposedScopes,
                    "JEV 제공자가 설정되지 않았거나 현재 사용할 수 없습니다.",
                    ProviderErrorCode.NOT_CONFIGURED);
        }
        return heuristic(prompt, candidateFiles, proposedScopes,
                "프로젝트 개인정보 설정에 따라 외부 JEV 판단을 사용하지 않았습니다.", null);
    }

    private DecisionResult heuristic(String prompt, List<String> files, List<TaskScope> scopes,
                                     String fallbackReason, ProviderErrorCode errorCode) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        String action;
        String risk;
        boolean approval;
        if (containsAny(lower, "삭제", "delete", "remove")) {
            action = "DELETE_FILES"; risk = "HIGH"; approval = true;
        } else if (containsAny(lower, "이동", "이름 변경", "move", "rename")) {
            action = "MODIFY_FILES"; risk = "MEDIUM"; approval = true;
        } else if (containsAny(lower, "생성", "추가", "만들", "create", "add", "implement")) {
            action = "CREATE_FILES"; risk = "MEDIUM"; approval = true;
        } else if (containsAny(lower, "수정", "변경", "고쳐", "fix", "update", "refactor")) {
            action = "MODIFY_FILES"; risk = "MEDIUM"; approval = true;
        } else if (containsAny(lower, "찾", "확인", "분석", "inspect", "explain", "review")) {
            action = "INSPECT_FILES"; risk = "LOW"; approval = false;
        } else {
            action = "ANSWER_ONLY"; risk = "LOW"; approval = false;
        }
        if (scopes.size() > 1 && !action.equals("ANSWER_ONLY") && !action.equals("INSPECT_FILES")) {
            action = "MIXED";
        }
        return DecisionResult.fallback(action, bestTarget(lower, files), scopes, risk, approval,
                fallbackReason, errorCode);
    }

    private void validate(DecisionResult result, List<String> files, List<String> tags,
                          List<TaskScope> proposedScopes) {
        if (result == null) throw ProviderCallException.invalidResponse(
                AiProviderType.JEV, "JEV가 비어 있는 판단 결과를 반환했습니다.");
        if (!ACTIONS.contains(result.actionType()) || !RISKS.contains(result.riskLevel())) {
            throw ProviderCallException.invalidResponse(
                    AiProviderType.JEV, "JEV가 허용되지 않은 작업 유형 또는 위험도를 반환했습니다.");
        }
        if (result.targetPath() != null && !files.contains(result.targetPath())) {
            throw ProviderCallException.invalidResponse(
                    AiProviderType.JEV, "JEV가 프로젝트에 없는 대상 파일을 반환했습니다.");
        }
        if (!tags.containsAll(result.selectedTags())) {
            throw ProviderCallException.invalidResponse(
                    AiProviderType.JEV, "JEV가 프로젝트에 없는 파일 태그를 반환했습니다.");
        }
        Set<String> instructions = proposedScopes.stream().map(TaskScope::instruction)
                .collect(java.util.stream.Collectors.toSet());
        for (TaskScope scope : result.taskScopes()) {
            if (!instructions.contains(scope.instruction()) || !tags.containsAll(scope.tags())) {
                throw ProviderCallException.invalidResponse(
                        AiProviderType.JEV, "JEV가 요청에 없는 작업 단위 또는 파일 태그를 반환했습니다.");
            }
        }
    }

    private String fallbackReason(RuntimeException exception) {
        if (exception instanceof ProviderCallException provider) {
            return "JEV 판단을 사용할 수 없어 로컬 휴리스틱으로 대체했습니다: "
                    + provider.errorCode().name() + " · " + provider.getMessage();
        }
        String message = exception.getMessage();
        return "JEV 판단 응답 검증에 실패해 로컬 휴리스틱으로 대체했습니다"
                + (message == null || message.isBlank() ? "." : ": " + message);
    }

    private String bestTarget(String prompt, List<String> files) {
        if (files == null) return null;
        return files.stream()
                .filter(path -> prompt.contains(path.toLowerCase(Locale.ROOT))
                        || prompt.contains(fileName(path).toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private DecisionResult mergeScopes(DecisionResult result, List<TaskScope> localScopes) {
        List<TaskScope> merged = new ArrayList<>();
        boolean hasJevScopes = !result.taskScopes().isEmpty();
        for (TaskScope local : localScopes) {
            TaskScope decided = result.taskScopes().stream()
                    .filter(scope -> scope.instruction().equals(local.instruction())).findFirst().orElse(null);
            if (hasJevScopes && decided == null) continue;
            Set<String> tags = new LinkedHashSet<>(local.tags());
            if (decided != null) tags.addAll(decided.tags());
            merged.add(new TaskScope(local.instruction(), List.copyOf(tags)));
        }
        if (merged.isEmpty()) merged.addAll(localScopes);
        List<String> selected = merged.stream().flatMap(scope -> scope.tags().stream()).distinct().toList();
        return new DecisionResult(result.actionType(), result.targetPath(), selected, merged,
                result.riskLevel(), result.needsHumanApproval(), result.model(), result.rawJson(),
                result.fallbackUsed(), result.fallbackReason(), result.fallbackProviderErrorCode());
    }

    private String state(String prompt, List<ProjectFile> files, List<TaskScope> scopes) {
        String candidates = files.isEmpty() ? "(파일 없음)" : files.stream()
                .map(file -> file.getRelativePath() + " tags=" + file.getTags())
                .reduce((left, right) -> left + "\n" + right).orElse("(파일 없음)");
        String tasks = scopes.stream().map(scope -> "- " + scope.instruction()).reduce(
                (left, right) -> left + "\n" + right).orElse("- " + prompt);
        return "사용자 요청:\n" + prompt + "\n\n분해된 작업 단위:\n" + tasks
                + "\n\n후보 파일과 태그:\n" + candidates;
    }
}

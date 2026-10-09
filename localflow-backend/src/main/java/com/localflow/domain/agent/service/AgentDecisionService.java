package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.TaskDecomposition;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionRequest;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.TaskScope;
import com.localflow.domain.provider.port.DecisionProvider;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentDecisionService {
    private final List<DecisionProvider> providers;
    private final PromptTaskDecomposer decomposer;

    public AgentDecisionService(List<DecisionProvider> providers, PromptTaskDecomposer decomposer) {
        this.providers = providers;
        this.decomposer = decomposer;
    }

    public DecisionResult decide(String prompt, List<ProjectFile> projectFiles, boolean externalAllowed,
                                 TaskDecomposition decomposition) {
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
                    DecisionResult result = jev.decide(new DecisionRequest(
                            state(prompt, projectFiles, proposedScopes), candidateFiles, candidateTags,
                            proposedScopes.stream().map(TaskScope::instruction).toList()));
                    return mergeScopes(result, proposedScopes);
                } catch (RuntimeException ignored) {
                    // Jev 장애가 전체 실행을 막지 않도록 로컬 규칙으로 판단을 계속한다.
                }
            }
        }
        return heuristic(prompt, candidateFiles, proposedScopes);
    }

    private DecisionResult heuristic(String prompt, List<String> files, List<TaskScope> scopes) {
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
        return DecisionResult.fallback(action, bestTarget(lower, files), scopes, risk, approval);
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
                result.riskLevel(), result.needsHumanApproval(), result.model(), result.rawJson());
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

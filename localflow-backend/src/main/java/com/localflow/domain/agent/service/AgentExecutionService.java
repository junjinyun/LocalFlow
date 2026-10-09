package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentFileChangeSnapshot;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.TaskDecomposition;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.PrivacyMode;
import com.localflow.domain.project.entity.ProjectSettings;
import com.localflow.domain.project.repository.ProjectSettingsRepository;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.port.GenerationProvider;
import com.localflow.domain.workspace.dto.FileMoveRequest;
import com.localflow.domain.workspace.dto.FileWriteRequest;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionService {
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final AgentDecisionService decisionService;
    private final AgentContextService contextService;
    private final AgentPlanParser planParser;
    private final TaskDecompositionParser decompositionParser;
    private final ProjectSettingsRepository settingsRepository;
    private final ProjectFileRepository fileRepository;
    private final ProjectFileService fileService;
    private final WorkspaceStorageService storageService;
    private final ChatMessageRepository chatRepository;
    private final ObjectMapper objectMapper;
    private final Map<AiProviderType, GenerationProvider> providers;

    public AgentExecutionService(AgentRunService runService,
                                 AgentRunRepository runRepository,
                                 AgentDecisionService decisionService,
                                 AgentContextService contextService,
                                 AgentPlanParser planParser,
                                 TaskDecompositionParser decompositionParser,
                                 ProjectSettingsRepository settingsRepository,
                                 ProjectFileRepository fileRepository,
                                 ProjectFileService fileService,
                                 WorkspaceStorageService storageService,
                                 ChatMessageRepository chatRepository,
                                 ObjectMapper objectMapper,
                                 List<GenerationProvider> providers) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.decisionService = decisionService;
        this.contextService = contextService;
        this.planParser = planParser;
        this.decompositionParser = decompositionParser;
        this.settingsRepository = settingsRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.storageService = storageService;
        this.chatRepository = chatRepository;
        this.objectMapper = objectMapper;
        this.providers = new EnumMap<>(AiProviderType.class);
        providers.forEach(provider -> this.providers.put(provider.providerType(), provider));
    }

    @Transactional
    public AgentRunResponse execute(String projectId, String runId, boolean approved) {
        AgentRun run = runService.requireRun(projectId, runId);
        if (run.getStatus() != AgentRunStatus.DRAFT && run.getStatus() != AgentRunStatus.WAITING_APPROVAL) {
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }
        try {
            if (run.getStatus() == AgentRunStatus.WAITING_APPROVAL && run.getPlanJson() != null) {
                if (!approved) return AgentRunResponse.from(run);
                AgentPlan storedPlan = planParser.parse(run.getPlanJson());
                run.startRunning(snapshotJson(projectId, storedPlan));
                apply(projectId, storedPlan);
                run.complete(run.getDecisionJson(), run.getPlanJson(), storedPlan.response(),
                        run.getModel(), run.getInputTokens(), run.getOutputTokens());
                addSystemMessage(run, storedPlan.response());
                return AgentRunResponse.from(runRepository.save(run));
            }

            ProjectSettings settings = requireSettings(projectId);
            List<ProjectFile> files = contextService.files(projectId);
            GenerationProvider provider = selectProvider(run.getPreferredGenerationProvider());
            ensurePrivacy(settings, provider.providerType());
            String selectedModel = selectModel(provider, run.getPreferredModel());
            run.startDeciding();
            DecompositionOutcome decomposition = decompose(run, files, provider, selectedModel);
            String decompositionJson = decompositionParser.toJson(decomposition.value());
            GenerationResult decompositionGeneration = decomposition.generation();
            Integer decompositionInputTokens = decompositionGeneration == null
                    ? run.getInputTokens() : decompositionGeneration.inputTokens();
            Integer decompositionOutputTokens = decompositionGeneration == null
                    ? run.getOutputTokens() : decompositionGeneration.outputTokens();
            run.recordDecomposition(decompositionJson, provider.providerType(),
                    decompositionGeneration == null ? selectedModel : decompositionGeneration.model(),
                    decompositionInputTokens, decompositionOutputTokens);
            DecisionResult decision = decisionService.decide(run.getPrompt(), files,
                    settings.getPrivacyMode() == PrivacyMode.EXTERNAL_ALLOWED, decomposition.value());
            String decisionJson = objectMapper.writeValueAsString(decision);

            if (settings.getReadPolicy() == PermissionPolicy.CONFIRM && !files.isEmpty() && !approved) {
                run.waitForApproval(decisionJson, null, null,
                        "관련 파일 내용을 AI 맥락으로 읽기 전에 승인이 필요합니다.",
                        decompositionGeneration == null ? selectedModel : decompositionGeneration.model(),
                        decompositionInputTokens, decompositionOutputTokens);
                return AgentRunResponse.from(runRepository.save(run));
            }

            boolean includeContents = settings.getReadPolicy() != PermissionPolicy.DENY;
            String context = contextService.build(run.getProject(), run.getPrompt(), decision, includeContents);
            run.startPlanning(provider.providerType());
            GenerationResult generation = provider.generate(new GenerationRequest(
                    systemPrompt(), userPrompt(run.getPrompt(), decomposition.value(), decision, context), selectedModel));
            AgentPlan plan = planParser.parse(generation.text());
            String planJson = planParser.toJson(plan);
            validatePolicies(settings, plan);
            String changesJson = snapshotJson(projectId, plan);
            Integer inputTokens = sumTokens(
                    decompositionInputTokens, generation.inputTokens());
            Integer outputTokens = sumTokens(
                    decompositionOutputTokens, generation.outputTokens());

            if (needsApproval(run.getExecutionMode(), settings, decision, plan) && !approved) {
                run.waitForApproval(decisionJson, planJson, changesJson, plan.response(), generation.model(),
                        inputTokens, outputTokens);
                return AgentRunResponse.from(runRepository.save(run));
            }

            run.startRunning(changesJson);
            apply(projectId, plan);
            run.complete(decisionJson, planJson, plan.response(), generation.model(),
                    inputTokens, outputTokens);
            addSystemMessage(run, plan.response());
            return AgentRunResponse.from(runRepository.save(run));
        } catch (Exception exception) {
            run.fail(safeMessage(exception));
            return AgentRunResponse.from(runRepository.save(run));
        }
    }

    @Transactional
    public AgentRunResponse findWithSnapshot(String projectId, String runId) {
        AgentRun run = runService.requireRun(projectId, runId);
        if (run.getStatus() == AgentRunStatus.WAITING_APPROVAL
                && run.getChangesJson() == null && run.getPlanJson() != null) {
            try {
                AgentPlan plan = planParser.parse(run.getPlanJson());
                run.recordChanges(snapshotJson(projectId, plan));
                runRepository.save(run);
            } catch (Exception exception) {
                throw new IllegalStateException("파일 변경 미리보기를 생성하지 못했습니다.", exception);
            }
        }
        return AgentRunResponse.from(run);
    }

    private GenerationProvider selectProvider(AiProviderType preferred) {
        if (preferred != null) {
            GenerationProvider selected = providers.get(preferred);
            if (selected == null || !selected.available()) {
                throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
            }
            return selected;
        }
        for (AiProviderType type : List.of(AiProviderType.OLLAMA, AiProviderType.OPENAI, AiProviderType.VERTEX_AI)) {
            GenerationProvider candidate = providers.get(type);
            if (candidate != null && candidate.available()) return candidate;
        }
        throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
    }

    private void ensurePrivacy(ProjectSettings settings, AiProviderType provider) {
        if (settings.getPrivacyMode() == PrivacyMode.LOCAL_ONLY && provider != AiProviderType.OLLAMA) {
            throw new CustomException(ErrorCode.EXTERNAL_PROVIDER_DENIED);
        }
    }

    private String selectModel(GenerationProvider provider, String preferredModel) {
        String selected = preferredModel == null || preferredModel.isBlank()
                ? provider.model() : preferredModel.strip();
        if (selected == null || !provider.models().contains(selected)) {
            throw new CustomException(ErrorCode.AI_MODEL_NOT_SUPPORTED);
        }
        return selected;
    }

    private boolean needsApproval(ExecutionMode mode, ProjectSettings settings,
                                  DecisionResult decision, AgentPlan plan) {
        if (plan.operations().isEmpty()) return false;
        if (mode == ExecutionMode.CONFIRM_EVERY_STEP) return true;
        if (plan.operations().stream().anyMatch(operation -> policy(settings, operation.action()) == PermissionPolicy.CONFIRM)) {
            return true;
        }
        return mode == ExecutionMode.BALANCED && decision.needsHumanApproval();
    }

    private void validatePolicies(ProjectSettings settings, AgentPlan plan) {
        if (plan.operations().stream().anyMatch(operation -> policy(settings, operation.action()) == PermissionPolicy.DENY)) {
            throw new CustomException(ErrorCode.FILE_OPERATION_DENIED);
        }
    }

    private PermissionPolicy policy(ProjectSettings settings, FileOperationAction action) {
        return switch (action) {
            case CREATE -> settings.getCreatePolicy();
            case UPDATE -> settings.getEditPolicy();
            case MOVE -> settings.getMovePolicy();
            case DELETE -> settings.getDeletePolicy();
        };
    }

    private void apply(String projectId, AgentPlan plan) {
        for (FileOperationPlan operation : plan.operations()) {
            String path = operation.path();
            ProjectFile current = fileRepository.findByProject_IdAndRelativePath(projectId, path).orElse(null);
            switch (operation.action()) {
                case CREATE -> fileService.write(projectId, null,
                        new FileWriteRequest(path, operation.content(), true));
                case UPDATE -> {
                    if (current == null) throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
                    fileService.write(projectId, current.getId(),
                            new FileWriteRequest(path, operation.content(), true));
                }
                case MOVE -> {
                    if (current == null) throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
                    fileService.move(projectId, current.getId(),
                            new FileMoveRequest(operation.destinationPath(), true));
                }
                case DELETE -> {
                    if (current == null) throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
                    fileService.delete(projectId, current.getId(), true);
                }
            }
        }
    }

    private String snapshotJson(String projectId, AgentPlan plan) throws Exception {
        List<AgentFileChangeSnapshot> snapshots = new ArrayList<>();
        for (FileOperationPlan operation : plan.operations()) {
            ProjectFile current = fileRepository
                    .findByProject_IdAndRelativePath(projectId, operation.path())
                    .orElse(null);
            String beforeContent = readableContent(projectId, current);
            String afterContent = switch (operation.action()) {
                case CREATE, UPDATE -> operation.content();
                case MOVE -> beforeContent;
                case DELETE -> null;
            };
            snapshots.add(new AgentFileChangeSnapshot(operation.action(), operation.path(),
                    operation.destinationPath(), beforeContent, afterContent));
        }
        return objectMapper.writeValueAsString(snapshots);
    }

    private String readableContent(String projectId, ProjectFile file) {
        if (file == null || file.getCategory() == FileCategory.DOCUMENT) {
            return null;
        }
        return storageService.readText(projectId, file.getRelativePath());
    }

    private ProjectSettings requireSettings(String projectId) {
        return settingsRepository.findByProject_Id(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_SETTINGS_NOT_FOUND));
    }

    private void addSystemMessage(AgentRun run, String message) {
        chatRepository.save(ChatMessage.create(run.getProject(), ChatMessageType.SYSTEM,
                "[AI 실행 완료] " + message));
    }

    private String systemPrompt() {
        return """
                당신은 업로드된 프로젝트 사본을 수정하는 개발 보조 에이전트다.
                반드시 JSON 객체 하나만 반환하고 마크다운 코드 펜스를 사용하지 마라.
                스키마: {"summary":"짧은 계획","response":"사용자에게 보여줄 한국어 결과",
                "operations":[{"action":"CREATE|UPDATE|MOVE|DELETE","path":"상대경로",
                "destinationPath":"MOVE일 때 상대경로 또는 null","content":"CREATE/UPDATE일 때 파일 전체 내용 또는 null"}]}
                질문에 답하거나 분석만 하면 operations는 빈 배열로 반환한다.
                UPDATE content에는 일부 패치가 아니라 저장할 파일 전체 내용을 넣는다.
                제공되지 않은 파일을 추측해서 수정하지 말고, 최소한의 파일만 변경한다.
                판단 결과에 작업 단위가 여러 개 있으면 순서를 유지하고 모든 작업 단위를 빠짐없이 반영한다.
                AI 작업 분해 결과는 후보이며 판단 결과 taskScopes에 없는 후보 작업은 수행하지 않는다.
                절대경로와 상위 디렉터리(..) 경로는 사용하지 않는다.
                """;
    }

    private DecompositionOutcome decompose(AgentRun run, List<ProjectFile> files,
                                           GenerationProvider provider, String selectedModel) {
        if (run.getDecompositionJson() != null && !run.getDecompositionJson().isBlank()) {
            try {
                return new DecompositionOutcome(decompositionParser.parse(run.getDecompositionJson()), null);
            } catch (RuntimeException ignored) {
                // 저장된 이전 결과가 손상된 경우에만 다시 분해한다.
            }
        }
        List<String> availableTags = files.stream().flatMap(file -> file.getTags().stream())
                .distinct().sorted().toList();
        GenerationResult generation = null;
        try {
            generation = provider.generate(new GenerationRequest(
                    decompositionSystemPrompt(),
                    decompositionUserPrompt(run.getPrompt(), contextService.buildDecompositionContext(run.getProject())),
                    selectedModel));
            return new DecompositionOutcome(decompositionParser.parse(generation.text()), generation);
        } catch (RuntimeException exception) {
            return new DecompositionOutcome(
                    decompositionParser.fallback(run.getPrompt(), availableTags), generation);
        }
    }

    private String decompositionSystemPrompt() {
        return """
                당신은 개발 요청을 실행하지 않고 작업 단위로 설계하는 분석기다.
                반드시 JSON 객체 하나만 반환하고 마크다운 코드 펜스를 사용하지 마라.
                최대 5개의 독립 작업으로 의미에 따라 분해하고 원래 수행 순서를 유지한다.
                코드나 파일 본문을 작성하지 말고, 제공된 프로젝트 태그를 우선 사용한다.
                단순 요청은 작업 하나로 유지한다.
                스키마: {"summary":"분해 요약","tasks":[{"id":"task-1",
                "instruction":"수행할 작업","dependsOn":["선행 작업 id"],
                "suggestedTags":["관련 태그"],"acceptanceCriteria":["완료 조건"]}]}
                """;
    }

    private String decompositionUserPrompt(String prompt, String indexContext) {
        return "사용자 요청:\n" + prompt + "\n\n프로젝트 인덱스:\n" + indexContext;
    }

    private String userPrompt(String prompt, TaskDecomposition decomposition,
                              DecisionResult decision, String context) {
        return "AI 작업 분해 결과: " + decomposition + "\n\nJev 또는 로컬 판단 결과: " + decision
                + "\n\n사용자 요청:\n" + prompt
                + "\n\n프로젝트 맥락:\n" + context;
    }

    private Integer sumTokens(Integer first, Integer second) {
        if (first == null && second == null) return null;
        return (first == null ? 0 : first) + (second == null ? 0 : second);
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof CustomException custom) return custom.errorCode().message();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "AI 실행 중 오류가 발생했습니다.";
        return message.length() <= 1800 ? message : message.substring(0, 1800) + "…";
    }

    private record DecompositionOutcome(TaskDecomposition value, GenerationResult generation) {
    }
}

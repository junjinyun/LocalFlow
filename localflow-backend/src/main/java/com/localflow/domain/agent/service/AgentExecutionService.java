package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentFileChangeSnapshot;
import com.localflow.domain.agent.domain.AgentInputSnapshot;
import com.localflow.domain.agent.domain.AgentInputSnapshot.InputBudget;
import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.AgentTaskExecution;
import com.localflow.domain.agent.domain.AgentTaskStatus;
import com.localflow.domain.agent.domain.DecomposedTask;
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
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.service.SensitiveDataMasker;
import com.localflow.domain.provider.service.OllamaGenerationProvider;
import com.localflow.domain.provider.service.OllamaLocalPolicy;
import com.localflow.domain.provider.service.StructuredGenerationService;
import com.localflow.domain.provider.service.ProviderRetryExecutor;
import com.localflow.domain.provider.service.StructuredGenerationService.ParsedGeneration;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AgentExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final AgentDecisionService decisionService;
    private final AgentContextService contextService;
    private final AgentContextBudgetPolicy contextBudgetPolicy;
    private final AgentPlanParser planParser;
    private final TaskDecompositionParser decompositionParser;
    private final TaskDependencyPlanner taskDependencyPlanner;
    private final AgentOutputSchemas outputSchemas;
    private final StructuredGenerationService structuredGenerationService;
    private final ProviderRetryExecutor retryExecutor;
    private final OllamaLocalPolicy ollamaLocalPolicy;
    private final ProjectSettingsRepository settingsRepository;
    private final ProjectFileRepository fileRepository;
    private final AgentFileChangeTransactionService fileChangeTransactionService;
    private final WorkspaceStorageService storageService;
    private final ChatMessageRepository chatRepository;
    private final AgentRunProgressService progressService;
    private final AgentRunCancellationService cancellationService;
    private final ObjectMapper objectMapper;
    private final Map<AiProviderType, GenerationProvider> providers;

    public AgentExecutionService(AgentRunService runService,
                                 AgentRunRepository runRepository,
                                 AgentDecisionService decisionService,
                                 AgentContextService contextService,
                                 AgentContextBudgetPolicy contextBudgetPolicy,
                                 AgentPlanParser planParser,
                                 TaskDecompositionParser decompositionParser,
                                 TaskDependencyPlanner taskDependencyPlanner,
                                 AgentOutputSchemas outputSchemas,
                                 StructuredGenerationService structuredGenerationService,
                                 ProviderRetryExecutor retryExecutor,
                                 OllamaLocalPolicy ollamaLocalPolicy,
                                 ProjectSettingsRepository settingsRepository,
                                 ProjectFileRepository fileRepository,
                                 AgentFileChangeTransactionService fileChangeTransactionService,
                                 WorkspaceStorageService storageService,
                                 ChatMessageRepository chatRepository,
                                 AgentRunProgressService progressService,
                                 AgentRunCancellationService cancellationService,
                                 ObjectMapper objectMapper,
                                 List<GenerationProvider> providers) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.decisionService = decisionService;
        this.contextService = contextService;
        this.contextBudgetPolicy = contextBudgetPolicy;
        this.planParser = planParser;
        this.decompositionParser = decompositionParser;
        this.taskDependencyPlanner = taskDependencyPlanner;
        this.outputSchemas = outputSchemas;
        this.structuredGenerationService = structuredGenerationService;
        this.retryExecutor = retryExecutor;
        this.ollamaLocalPolicy = ollamaLocalPolicy;
        this.settingsRepository = settingsRepository;
        this.fileRepository = fileRepository;
        this.fileChangeTransactionService = fileChangeTransactionService;
        this.storageService = storageService;
        this.chatRepository = chatRepository;
        this.progressService = progressService;
        this.cancellationService = cancellationService;
        this.objectMapper = objectMapper;
        this.providers = new EnumMap<>(AiProviderType.class);
        providers.forEach(provider -> this.providers.put(provider.providerType(), provider));
    }

    public void processQueued(String projectId, String runId, boolean approved, boolean approvalResume,
                              List<String> selectedOperationIds) {
        AgentRun run = runService.requireRun(projectId, runId);
        if (run.getStatus() != AgentRunStatus.PENDING) return;
        try {
            cancellationService.checkpoint(projectId, runId);
            progressService.append(projectId, runId, AgentProgressStage.PREPARING,
                    "요청을 확인하고 실행 환경을 준비하는 중입니다.");
            if (approvalResume && run.getPlanJson() != null && run.getTaskExecutionsJson() == null) {
                AgentPlan originalPlan = planParser.parse(run.getPlanJson());
                AgentPlan storedPlan = AgentPlanSelection.select(originalPlan, selectedOperationIds);
                cancellationService.checkpoint(projectId, runId);
                progressService.append(projectId, runId, AgentProgressStage.APPLYING,
                        "승인된 파일 변경을 업로드된 프로젝트에 적용하는 중입니다.");
                run.startRunning(snapshotJson(projectId, storedPlan));
                runRepository.saveAndFlush(run);
                cancellationService.checkpoint(projectId, runId);
                var applyResult = fileChangeTransactionService.apply(projectId, storedPlan,
                        () -> cancellationService.checkpoint(projectId, runId));
                run.recordApplicationResult(objectMapper.writeValueAsString(applyResult));
                cancellationService.checkpoint(projectId, runId);
                run.complete(run.getDecisionJson(), run.getPlanJson(), storedPlan.response(),
                        run.getModel(), run.getInputTokens(), run.getOutputTokens());
                runRepository.saveAndFlush(run);
                addSystemMessage(run, storedPlan.response());
                progressService.append(projectId, runId, AgentProgressStage.COMPLETED,
                        "파일 변경 적용과 결과 정리를 완료했습니다.");
                return;
            }

            ProjectSettings settings = requireSettings(projectId);
            List<ProjectFile> files = contextService.files(projectId);
            GenerationProvider provider = selectProvider(run.getPreferredGenerationProvider(), settings);
            String requestedModel = run.getPreferredModel() == null || run.getPreferredModel().isBlank()
                    ? provider.model() : run.getPreferredModel().strip();
            ensurePrivacy(settings, provider, requestedModel);
            if (!provider.available()) throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
            String selectedModel = selectModel(provider, requestedModel);
            ensurePrivacy(settings, provider, selectedModel);
            cancellationService.checkpoint(projectId, runId);
            run.startDeciding();
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.DECOMPOSING,
                    "선택한 AI가 요청을 실행 가능한 작업 단위로 분해하는 중입니다.");
            DecompositionOutcome decomposition = decompose(run, files, provider, selectedModel);
            cancellationService.checkpoint(projectId, runId);
            String decompositionJson = decompositionParser.toJson(decomposition.value());
            GenerationResult decompositionGeneration = decomposition.generation();
            Integer decompositionInputTokens = decompositionGeneration == null
                    ? run.getInputTokens() : decompositionGeneration.inputTokens();
            Integer decompositionOutputTokens = decompositionGeneration == null
                    ? run.getOutputTokens() : decompositionGeneration.outputTokens();
            run.recordDecomposition(decompositionJson, provider.providerType(),
                    decompositionGeneration == null ? selectedModel : decompositionGeneration.model(),
                    decompositionInputTokens, decompositionOutputTokens);
            runRepository.saveAndFlush(run);
            if (decomposition.fallbackReason() != null) {
                progressService.append(projectId, runId, AgentProgressStage.DECOMPOSED,
                        "AI 구조화 응답을 해석하지 못해 로컬 규칙으로 요청을 "
                                + decomposition.value().tasks().size() + "개의 작업 단위로 정리했습니다.");
            } else if (decomposition.attempts() > 1) {
                progressService.append(projectId, runId, AgentProgressStage.DECOMPOSED,
                        "구조화 응답을 재시도한 뒤 요청을 "
                                + decomposition.value().tasks().size() + "개의 작업 단위로 정리했습니다.");
            } else {
                progressService.append(projectId, runId, AgentProgressStage.DECOMPOSED,
                        "요청을 " + decomposition.value().tasks().size() + "개의 작업 단위로 정리했습니다.");
            }
            progressService.append(projectId, runId, AgentProgressStage.DECIDING,
                    "Jev가 관련 태그, 대상 범위와 작업 위험도를 판단하는 중입니다.");
            DecisionResult decision = decisionService.decide(run.getPrompt(), files,
                    settings.getPrivacyMode() == PrivacyMode.EXTERNAL_ALLOWED, decomposition.value(),
                    () -> cancellationService.checkpoint(projectId, runId),
                    notice -> recordProviderRetry(projectId, run, AgentProgressStage.DECIDING, notice));
            cancellationService.checkpoint(projectId, runId);
            String decisionJson = objectMapper.writeValueAsString(decision);

            if (settings.getReadPolicy() == PermissionPolicy.CONFIRM && !files.isEmpty() && !approved) {
                run.waitForApproval(decisionJson, null, null,
                        "관련 파일 내용을 AI 맥락으로 읽기 전에 승인이 필요합니다.",
                        decompositionGeneration == null ? selectedModel : decompositionGeneration.model(),
                        decompositionInputTokens, decompositionOutputTokens);
                progressService.append(projectId, runId, AgentProgressStage.WAITING_APPROVAL,
                        "관련 파일 내용을 AI에 전달하기 전에 사용자 승인을 기다리고 있습니다.");
                runRepository.saveAndFlush(run);
                return;
            }

            List<DecomposedTask> orderedTasks = taskDependencyPlanner.order(decomposition.value().tasks());
            if (orderedTasks.size() > 1 || run.getTaskExecutionsJson() != null) {
                executeTaskSequence(projectId, runId, run, settings, provider, selectedModel,
                        decomposition.value(), orderedTasks, decompositionInputTokens,
                        decompositionOutputTokens, approvalResume, selectedOperationIds);
                return;
            }

            boolean includeContents = settings.getReadPolicy() != PermissionPolicy.DENY;
            progressService.append(projectId, runId, AgentProgressStage.SELECTING_CONTEXT,
                    "태그와 프로젝트 인덱스를 기준으로 관련 파일을 찾는 중입니다.");
            AgentContextBudgetPolicy.Budget contextBudget = contextBudgetPolicy.resolve(
                    provider.providerType(), selectedModel);
            AgentContextService.ContextSnapshot contextSnapshot = contextService.buildSnapshot(
                    run.getProject(), run.getPrompt(), decision, includeContents,
                    contextBudget.maxContextChars());
            cancellationService.checkpoint(projectId, runId);
            String systemPrompt = systemPrompt();
            String userPrompt = userPrompt(run.getPrompt(), decomposition.value(), decision,
                    contextSnapshot.content());
            GenerationRequest generationRequest = new GenerationRequest(
                    systemPrompt, userPrompt, selectedModel, outputSchemas.filePlan());
            GenerationRequest preparedRequest = generationRequest.withSchemaInstruction("파일 변경 계획");
            contextBudgetPolicy.validateRequest(contextBudget,
                    preparedRequest.systemPrompt(), preparedRequest.userPrompt());
            run.recordInputSnapshot(objectMapper.writeValueAsString(inputSnapshot(
                    contextSnapshot, preparedRequest, contextBudget)));
            run.startPlanning(provider.providerType());
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.PLANNING,
                    "선택한 AI가 코드와 파일 변경 계획을 생성하는 중입니다.");
            appendOllamaWaitProgress(projectId, runId, provider, AgentProgressStage.PLANNING);
            ParsedGeneration<AgentPlan> planGeneration = withProviderRetry(
                    projectId, run, AgentProgressStage.PLANNING,
                    () -> structuredGenerationService.generate(
                            provider, generationRequest, planParser::parse, "파일 변경 계획"));
            cancellationService.checkpoint(projectId, runId);
            GenerationResult generation = planGeneration.generation();
            AgentPlan plan = planGeneration.value();
            if (planGeneration.attempts() > 1) {
                progressService.append(projectId, runId, AgentProgressStage.PLANNING,
                        "파일 변경 계획의 구조화 응답을 재시도하여 정상 형식으로 복구했습니다.");
            }
            String planJson = planParser.toJson(plan);
            run.startValidating();
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                    "생성된 결과를 검증하고 파일 변경 전후 차이를 계산하는 중입니다.");
            validatePolicies(settings, plan);
            String changesJson = snapshotJson(projectId, plan);
            cancellationService.checkpoint(projectId, runId);
            Integer inputTokens = sumTokens(
                    decompositionInputTokens, generation.inputTokens());
            Integer outputTokens = sumTokens(
                    decompositionOutputTokens, generation.outputTokens());

            if (needsApproval(run.getExecutionMode(), settings, decision, plan) && !approved) {
                run.waitForApproval(decisionJson, planJson, changesJson, plan.response(), generation.model(),
                        inputTokens, outputTokens);
                progressService.append(projectId, runId, AgentProgressStage.WAITING_APPROVAL,
                        "변경 계획이 준비되어 사용자 승인을 기다리고 있습니다.");
                runRepository.saveAndFlush(run);
                return;
            }

            progressService.append(projectId, runId, AgentProgressStage.APPLYING,
                    "검증된 파일 변경을 업로드된 프로젝트에 적용하는 중입니다.");
            run.startRunning(changesJson);
            runRepository.saveAndFlush(run);
            cancellationService.checkpoint(projectId, runId);
            var applyResult = fileChangeTransactionService.apply(projectId, plan,
                    () -> cancellationService.checkpoint(projectId, runId));
            run.recordApplicationResult(objectMapper.writeValueAsString(applyResult));
            cancellationService.checkpoint(projectId, runId);
            run.complete(decisionJson, planJson, plan.response(), generation.model(),
                    inputTokens, outputTokens);
            runRepository.saveAndFlush(run);
            addSystemMessage(run, plan.response());
            progressService.append(projectId, runId, AgentProgressStage.COMPLETED,
                    "AI 작업과 결과 정리를 모두 완료했습니다.");
        } catch (AgentFileApplyException exception) {
            handleApplyFailure(projectId, runId, run, exception);
        } catch (AgentRunCancelledException exception) {
            cancellationService.complete(projectId, runId);
        } catch (Exception exception) {
            if (cancellationService.isCancellationRequested(projectId, runId)) {
                cancellationService.complete(projectId, runId);
                return;
            }
            if (exception instanceof ProviderCallException providerException) {
                log.warn("Agent provider execution failed: runId={}, provider={}, code={}, retryable={}, diagnostic={}",
                        runId, providerException.providerType(), providerException.errorCode(),
                        providerException.retryable(),
                        SensitiveDataMasker.mask(providerException.diagnosticMessage()));
                run.failProvider(providerException.getMessage(), providerException.errorCode(),
                        providerException.retryable(), providerException.recommendedAction());
            } else {
                run.fail(safeMessage(exception));
            }
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.FAILED,
                    "작업을 완료하지 못했습니다: " + safeMessage(exception));
        }
    }

    public void processRevision(String projectId, String runId, String revisionInstruction) {
        AgentRun run = runService.requireRun(projectId, runId);
        if (run.getStatus() != AgentRunStatus.PENDING || run.getPlanJson() == null) return;
        try {
            cancellationService.checkpoint(projectId, runId);
            progressService.append(projectId, runId, AgentProgressStage.PREPARING,
                    "기존 계획과 추가 수정 요청을 확인하는 중입니다.");
            ProjectSettings settings = requireSettings(projectId);
            GenerationProvider provider = selectProvider(run.getPreferredGenerationProvider(), settings);
            String requestedModel = run.getPreferredModel() == null || run.getPreferredModel().isBlank()
                    ? provider.model() : run.getPreferredModel().strip();
            String selectedModel = selectModel(provider, requestedModel);
            ensurePrivacy(settings, provider, selectedModel);
            if (!provider.available()) throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);

            AgentPlan previousPlan = planParser.parse(run.getPlanJson());
            TaskDecomposition decomposition = decompositionParser.parse(run.getDecompositionJson());
            DecisionResult decision = objectMapper.readValue(run.getDecisionJson(), DecisionResult.class);
            String combinedPrompt = run.getPrompt() + "\n\n추가 수정 요청:\n" + revisionInstruction;
            AgentContextBudgetPolicy.Budget contextBudget = contextBudgetPolicy.resolve(
                    provider.providerType(), selectedModel);
            AgentContextService.ContextSnapshot contextSnapshot = contextService.buildSnapshot(
                    run.getProject(), combinedPrompt, decision,
                    settings.getReadPolicy() != PermissionPolicy.DENY,
                    contextBudget.maxContextChars());
            String systemPrompt = systemPrompt() + "\n기존 계획을 그대로 반복하지 말고 추가 수정 요청을 반영한 새 전체 계획을 반환하라.";
            String userPrompt = userPrompt(combinedPrompt, decomposition, decision, contextSnapshot.content())
                    + "\n\n현재 승인 대기 계획:\n" + planParser.toJson(previousPlan);
            GenerationRequest request = new GenerationRequest(
                    systemPrompt, userPrompt, selectedModel, outputSchemas.filePlan());
            GenerationRequest prepared = request.withSchemaInstruction("수정된 파일 변경 계획");
            contextBudgetPolicy.validateRequest(contextBudget,
                    prepared.systemPrompt(), prepared.userPrompt());
            run.recordInputSnapshot(objectMapper.writeValueAsString(inputSnapshot(
                    contextSnapshot, prepared, contextBudget)));
            run.startPlanning(provider.providerType());
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.PLANNING,
                    "추가 요청을 반영하여 파일 변경 계획을 다시 생성하는 중입니다.");
            appendOllamaWaitProgress(projectId, runId, provider, AgentProgressStage.PLANNING);
            ParsedGeneration<AgentPlan> generated = withProviderRetry(
                    projectId, run, AgentProgressStage.PLANNING,
                    () -> structuredGenerationService.generate(
                            provider, request, planParser::parse, "수정된 파일 변경 계획"));
            AgentPlan revisedPlan = generated.value();
            validatePolicies(settings, revisedPlan);
            String revisedPlanJson = planParser.toJson(revisedPlan);
            String changesJson = snapshotJson(projectId, revisedPlan);
            refreshWaitingTaskPlan(run, revisedPlanJson, changesJson, revisedPlan.response());
            Integer inputTokens = sumTokens(run.getInputTokens(), generated.generation().inputTokens());
            Integer outputTokens = sumTokens(run.getOutputTokens(), generated.generation().outputTokens());
            run.waitForApproval(run.getDecisionJson(), revisedPlanJson, changesJson,
                    revisedPlan.response(), generated.generation().model(), inputTokens, outputTokens);
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.WAITING_APPROVAL,
                    "추가 요청을 반영한 새 계획이 준비되어 다시 승인을 기다리고 있습니다.");
        } catch (AgentRunCancelledException exception) {
            cancellationService.complete(projectId, runId);
        } catch (Exception exception) {
            if (exception instanceof ProviderCallException providerException) {
                run.failProvider(providerException.getMessage(), providerException.errorCode(),
                        providerException.retryable(), providerException.recommendedAction());
            } else run.fail(safeMessage(exception));
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.FAILED,
                    "추가 수정 계획을 생성하지 못했습니다: " + safeMessage(exception));
        }
    }

    private void refreshWaitingTaskPlan(AgentRun run, String planJson,
                                        String changesJson, String response) throws Exception {
        if (run.getTaskExecutionsJson() == null || run.getTaskExecutionsJson().isBlank()) return;
        var type = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, AgentTaskExecution.class);
        List<AgentTaskExecution> executions = new ArrayList<>(
                objectMapper.readValue(run.getTaskExecutionsJson(), type));
        for (int index = 0; index < executions.size(); index++) {
            AgentTaskExecution execution = executions.get(index);
            if (execution.status() == AgentTaskStatus.WAITING_APPROVAL) {
                executions.set(index, execution.withStatus(AgentTaskStatus.WAITING_APPROVAL,
                        planJson, changesJson, response, null));
                run.recordTaskExecutions(objectMapper.writeValueAsString(executions));
                return;
            }
        }
        throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
    }

    private void executeTaskSequence(String projectId, String runId, AgentRun run,
                                     ProjectSettings settings, GenerationProvider provider,
                                     String selectedModel, TaskDecomposition decomposition,
                                     List<DecomposedTask> orderedTasks,
                                     Integer initialInputTokens, Integer initialOutputTokens,
                                     boolean approvalResume, List<String> selectedOperationIds) throws Exception {
        List<AgentTaskExecution> executions = taskExecutions(run, orderedTasks);
        int inputTokens = initialInputTokens == null ? 0 : initialInputTokens;
        int outputTokens = initialOutputTokens == null ? 0 : initialOutputTokens;

        if (approvalResume) {
            int waitingIndex = findTask(executions, AgentTaskStatus.WAITING_APPROVAL);
            if (waitingIndex < 0 || run.getPlanJson() == null) {
                throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
            }
            AgentPlan approvedPlan = AgentPlanSelection.select(
                    planParser.parse(run.getPlanJson()), selectedOperationIds);
            progressService.append(projectId, runId, AgentProgressStage.APPLYING,
                    "승인된 작업 단위의 파일 변경을 적용하는 중입니다.");
            run.startRunning(snapshotJson(projectId, approvedPlan));
            runRepository.saveAndFlush(run);
            var applyResult = fileChangeTransactionService.apply(projectId, approvedPlan,
                    () -> cancellationService.checkpoint(projectId, runId));
            run.recordApplicationResult(objectMapper.writeValueAsString(applyResult));
            AgentTaskExecution waiting = executions.get(waitingIndex);
            executions.set(waitingIndex, waiting.withStatus(AgentTaskStatus.COMPLETED,
                    waiting.planJson(), snapshotJson(projectId, approvedPlan),
                    approvedPlan.response(), null));
            saveTaskExecutions(run, executions);
        }

        Map<String, AgentTaskStatus> statuses = new HashMap<>();
        executions.forEach(execution -> statuses.put(execution.taskId(), execution.status()));
        boolean includeContents = settings.getReadPolicy() != PermissionPolicy.DENY;
        AgentContextBudgetPolicy.Budget contextBudget = contextBudgetPolicy.resolve(
                provider.providerType(), selectedModel);
        boolean anyFailed = false;

        for (int index = 0; index < executions.size(); index++) {
            AgentTaskExecution execution = executions.get(index);
            if (execution.status() != AgentTaskStatus.PENDING) continue;
            if (execution.dependsOn().stream().anyMatch(dependency -> {
                AgentTaskStatus status = statuses.get(dependency);
                return status == AgentTaskStatus.FAILED || status == AgentTaskStatus.SKIPPED;
            })) {
                AgentTaskExecution skipped = execution.withStatus(AgentTaskStatus.SKIPPED,
                        null, null, null, "선행 작업이 완료되지 않아 실행하지 않았습니다.");
                executions.set(index, skipped);
                statuses.put(skipped.taskId(), skipped.status());
                saveTaskExecutions(run, executions);
                continue;
            }

            DecomposedTask task = orderedTasks.stream()
                    .filter(candidate -> candidate.id().equals(execution.taskId()))
                    .findFirst().orElseThrow(() -> new CustomException(ErrorCode.INVALID_TASK_DEPENDENCY));
            AgentTaskExecution running = execution.withStatus(AgentTaskStatus.RUNNING,
                    null, null, null, null);
            executions.set(index, running);
            statuses.put(running.taskId(), running.status());
            saveTaskExecutions(run, executions);
            progressService.append(projectId, runId, AgentProgressStage.DECIDING,
                    "작업 " + (index + 1) + "/" + executions.size() + "의 대상 파일과 위험도를 판단하는 중입니다.");

            String taskPlanJson = null;
            String taskChangesJson = null;
            try {
                cancellationService.checkpoint(projectId, runId);
                List<ProjectFile> currentFiles = contextService.files(projectId);
                TaskDecomposition singleTask = new TaskDecomposition(
                        task.instruction(), decomposition.source(), List.of(task));
                DecisionResult decision = decisionService.decide(task.instruction(), currentFiles,
                        settings.getPrivacyMode() == PrivacyMode.EXTERNAL_ALLOWED, singleTask,
                        () -> cancellationService.checkpoint(projectId, runId),
                        notice -> recordProviderRetry(projectId, run, AgentProgressStage.DECIDING, notice));
                String decisionJson = objectMapper.writeValueAsString(decision);
                AgentContextService.ContextSnapshot contextSnapshot = contextService.buildSnapshot(
                        run.getProject(), task.instruction(), decision, includeContents,
                        contextBudget.maxContextChars());
                String systemPrompt = systemPrompt();
                String taskPrompt = userPrompt(task.instruction(), singleTask, decision,
                        contextSnapshot.content());
                GenerationRequest request = new GenerationRequest(
                        systemPrompt, taskPrompt, selectedModel, outputSchemas.filePlan());
                GenerationRequest prepared = request.withSchemaInstruction("파일 변경 계획");
                contextBudgetPolicy.validateRequest(contextBudget,
                        prepared.systemPrompt(), prepared.userPrompt());
                run.recordInputSnapshot(objectMapper.writeValueAsString(inputSnapshot(
                        contextSnapshot, prepared, contextBudget)));
                run.startPlanning(provider.providerType());
                runRepository.saveAndFlush(run);
                progressService.append(projectId, runId, AgentProgressStage.PLANNING,
                        "작업 " + (index + 1) + "/" + executions.size() + "의 파일 변경 계획을 생성하는 중입니다.");
                appendOllamaWaitProgress(projectId, runId, provider, AgentProgressStage.PLANNING);
                ParsedGeneration<AgentPlan> generated = withProviderRetry(
                        projectId, run, AgentProgressStage.PLANNING,
                        () -> structuredGenerationService.generate(
                                provider, request, planParser::parse, "파일 변경 계획"));
                AgentPlan plan = generated.value();
                validatePolicies(settings, plan);
                String planJson = planParser.toJson(plan);
                String changesJson = snapshotJson(projectId, plan);
                taskPlanJson = planJson;
                taskChangesJson = changesJson;
                inputTokens += generated.generation().inputTokens() == null
                        ? 0 : generated.generation().inputTokens();
                outputTokens += generated.generation().outputTokens() == null
                        ? 0 : generated.generation().outputTokens();

                if (needsApproval(run.getExecutionMode(), settings, decision, plan)) {
                    AgentTaskExecution waiting = running.withStatus(AgentTaskStatus.WAITING_APPROVAL,
                            planJson, changesJson, plan.response(), null);
                    executions.set(index, waiting);
                    statuses.put(waiting.taskId(), waiting.status());
                    saveTaskExecutions(run, executions);
                    run.waitForApproval(decisionJson, planJson, changesJson,
                            "작업 " + (index + 1) + "의 파일 변경 승인이 필요합니다.",
                            generated.generation().model(), inputTokens, outputTokens);
                    runRepository.saveAndFlush(run);
                    progressService.append(projectId, runId, AgentProgressStage.WAITING_APPROVAL,
                            "작업 " + (index + 1) + "/" + executions.size()
                                    + "의 변경 계획이 준비되어 승인을 기다리고 있습니다.");
                    return;
                }

                run.startRunning(changesJson);
                runRepository.saveAndFlush(run);
                progressService.append(projectId, runId, AgentProgressStage.APPLYING,
                        "작업 " + (index + 1) + "/" + executions.size() + "의 파일 변경을 적용하는 중입니다.");
                var applyResult = fileChangeTransactionService.apply(projectId, plan,
                        () -> cancellationService.checkpoint(projectId, runId));
                run.recordApplicationResult(objectMapper.writeValueAsString(applyResult));
                AgentTaskExecution completed = running.withStatus(AgentTaskStatus.COMPLETED,
                        planJson, changesJson, plan.response(), null);
                executions.set(index, completed);
                statuses.put(completed.taskId(), completed.status());
                saveTaskExecutions(run, executions);
            } catch (AgentRunCancelledException exception) {
                throw exception;
            } catch (AgentFileApplyException exception) {
                if (!exception.result().rollbackSuccessful()) throw exception;
                anyFailed = true;
                AgentTaskExecution failed = running.withStatus(AgentTaskStatus.FAILED,
                        taskPlanJson, taskChangesJson, null, safeMessage(exception));
                executions.set(index, failed);
                statuses.put(failed.taskId(), failed.status());
                saveTaskExecutions(run, executions);
            } catch (Exception exception) {
                anyFailed = true;
                AgentTaskExecution failed = running.withStatus(AgentTaskStatus.FAILED,
                        taskPlanJson, taskChangesJson, null, safeMessage(exception));
                executions.set(index, failed);
                statuses.put(failed.taskId(), failed.status());
                saveTaskExecutions(run, executions);
            }
        }

        List<FileOperationPlan> combinedOperations = new ArrayList<>();
        List<String> results = new ArrayList<>();
        for (AgentTaskExecution execution : executions) {
            if (execution.planJson() != null && execution.status() == AgentTaskStatus.COMPLETED) {
                AgentPlan taskPlan = planParser.parse(execution.planJson());
                combinedOperations.addAll(taskPlan.operations());
            }
            if (execution.resultMessage() != null) results.add(execution.resultMessage());
        }
        AgentPlan combined = new AgentPlan(decomposition.summary(),
                results.isEmpty() ? "분해된 작업 실행을 완료했습니다." : String.join("\n\n", results),
                combinedOperations);
        if (anyFailed || executions.stream().anyMatch(value -> value.status() == AgentTaskStatus.FAILED)) {
            run.fail("일부 작업 단위가 실패했습니다. 실행 상세에서 작업별 결과를 확인해 주세요.");
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.FAILED,
                    "독립 작업은 계속 처리했지만 일부 작업 단위를 완료하지 못했습니다.");
            return;
        }
        String combinedJson = planParser.toJson(combined);
        run.complete(run.getDecisionJson(), combinedJson, combined.response(), selectedModel,
                inputTokens, outputTokens);
        runRepository.saveAndFlush(run);
        addSystemMessage(run, combined.response());
        progressService.append(projectId, runId, AgentProgressStage.COMPLETED,
                "의존 순서에 따른 모든 작업 단위 실행을 완료했습니다.");
    }

    private List<AgentTaskExecution> taskExecutions(AgentRun run,
                                                    List<DecomposedTask> orderedTasks) throws Exception {
        if (run.getTaskExecutionsJson() != null && !run.getTaskExecutionsJson().isBlank()) {
            var type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, AgentTaskExecution.class);
            return new ArrayList<>(objectMapper.readValue(run.getTaskExecutionsJson(), type));
        }
        List<AgentTaskExecution> created = new ArrayList<>();
        for (int index = 0; index < orderedTasks.size(); index++) {
            DecomposedTask task = orderedTasks.get(index);
            created.add(new AgentTaskExecution(task.id(), task.instruction(), task.dependsOn(),
                    index + 1, AgentTaskStatus.PENDING, null, null, null, null));
        }
        saveTaskExecutions(run, created);
        return created;
    }

    private void saveTaskExecutions(AgentRun run, List<AgentTaskExecution> executions) throws Exception {
        run.recordTaskExecutions(objectMapper.writeValueAsString(executions));
        runRepository.saveAndFlush(run);
    }

    private int findTask(List<AgentTaskExecution> executions, AgentTaskStatus status) {
        for (int index = 0; index < executions.size(); index++) {
            if (executions.get(index).status() == status) return index;
        }
        return -1;
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

    private GenerationProvider selectProvider(AiProviderType preferred, ProjectSettings settings) {
        if (settings.getPrivacyMode() == PrivacyMode.LOCAL_ONLY) {
            if (preferred != null && preferred != AiProviderType.OLLAMA) {
                throw new CustomException(ErrorCode.EXTERNAL_PROVIDER_DENIED);
            }
            GenerationProvider local = providers.get(AiProviderType.OLLAMA);
            if (local == null) throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
            return local;
        }
        if (preferred != null) {
            GenerationProvider selected = providers.get(preferred);
            if (selected == null || !selected.available()) {
                throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
            }
            return selected;
        }
        for (AiProviderType type : List.of(AiProviderType.OLLAMA, AiProviderType.GEMINI_CLI,
                AiProviderType.OPENAI, AiProviderType.VERTEX_AI)) {
            GenerationProvider candidate = providers.get(type);
            if (candidate != null && candidate.available()) return candidate;
        }
        throw new CustomException(ErrorCode.PROVIDER_NOT_CONFIGURED);
    }

    private void ensurePrivacy(ProjectSettings settings, GenerationProvider provider, String model) {
        if (settings.getPrivacyMode() != PrivacyMode.LOCAL_ONLY) return;
        if (provider.providerType() != AiProviderType.OLLAMA) {
            throw new CustomException(ErrorCode.EXTERNAL_PROVIDER_DENIED);
        }
        ollamaLocalPolicy.validate(model);
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

    private void handleApplyFailure(String projectId, String runId, AgentRun run,
                                    AgentFileApplyException exception) {
        try {
            String resultJson = objectMapper.writeValueAsString(exception.result());
            if (exception.result().rollbackSuccessful()
                    && cancellationService.isCancellationRequested(projectId, runId)) {
                cancellationService.complete(projectId, runId);
                return;
            }
            if (!exception.result().rollbackSuccessful()) {
                run.requireRecovery(exception.getMessage(), resultJson);
                runRepository.saveAndFlush(run);
                progressService.append(projectId, runId, AgentProgressStage.RECOVERY_REQUIRED,
                        "일부 파일을 자동 복구하지 못했습니다. 실행 상세에서 복구 대상 파일을 확인해 주세요.");
                return;
            }
            run.recordApplicationResult(resultJson);
            run.fail(exception.getMessage());
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.ROLLED_BACK,
                    "파일 변경 중 오류가 발생해 적용된 변경을 모두 원본 상태로 복구했습니다.");
        } catch (Exception handlingException) {
            run.requireRecovery("파일 변경 실패 결과를 기록하지 못했습니다.", null);
            runRepository.saveAndFlush(run);
            progressService.append(projectId, runId, AgentProgressStage.RECOVERY_REQUIRED,
                    "파일 변경 실패 결과를 기록하지 못했습니다. 프로젝트 파일을 확인해 주세요.");
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
            snapshots.add(new AgentFileChangeSnapshot(operation.id(), operation.action(), operation.path(),
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
                전달된 파일 변경 계획 JSON Schema와 일치하는 JSON 객체 하나만 반환하라.
                마크다운 코드 펜스, 설명문, 스키마에 없는 필드는 사용하지 마라.
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
                return new DecompositionOutcome(
                        decompositionParser.parse(run.getDecompositionJson()), null, 0, null);
            } catch (RuntimeException ignored) {
                // 저장된 이전 결과가 손상된 경우에만 다시 분해한다.
            }
        }
        List<String> availableTags = files.stream().flatMap(file -> file.getTags().stream())
                .distinct().sorted().toList();
        GenerationResult generation = null;
        try {
            appendOllamaWaitProgress(run.getProject().getId(), run.getId(), provider,
                    AgentProgressStage.DECOMPOSING);
            ParsedGeneration<TaskDecomposition> parsed = withProviderRetry(
                    run.getProject().getId(), run, AgentProgressStage.DECOMPOSING,
                    () -> structuredGenerationService.generate(
                            provider,
                            new GenerationRequest(
                                    decompositionSystemPrompt(),
                                    decompositionUserPrompt(
                                            run.getPrompt(),
                                            contextService.buildDecompositionContext(run.getProject())),
                                    selectedModel,
                                    outputSchemas.taskDecomposition()),
                            decompositionParser::parse,
                            "작업 분해"));
            generation = parsed.generation();
            return new DecompositionOutcome(parsed.value(), generation, parsed.attempts(), null);
        } catch (RuntimeException exception) {
            return new DecompositionOutcome(
                    decompositionParser.fallback(run.getPrompt(), availableTags),
                    generation, 0, structureFailureMessage(exception));
        }
    }

    private String decompositionSystemPrompt() {
        return """
                당신은 개발 요청을 실행하지 않고 작업 단위로 설계하는 분석기다.
                전달된 작업 분해 JSON Schema와 일치하는 JSON 객체 하나만 반환하라.
                마크다운 코드 펜스, 설명문, 스키마에 없는 필드는 사용하지 마라.
                최대 5개의 독립 작업으로 의미에 따라 분해하고 원래 수행 순서를 유지한다.
                코드나 파일 본문을 작성하지 말고, 제공된 프로젝트 태그를 우선 사용한다.
                단순 요청은 작업 하나로 유지한다.
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

    private AgentInputSnapshot inputSnapshot(AgentContextService.ContextSnapshot context,
                                             GenerationRequest request,
                                             AgentContextBudgetPolicy.Budget budget) {
        int usedChars = (request.systemPrompt() == null ? 0 : request.systemPrompt().length())
                + (request.userPrompt() == null ? 0 : request.userPrompt().length());
        InputBudget inputBudget = new InputBudget(
                budget.maxInputChars(), usedChars, (usedChars + 3) / 4,
                context.files(), context.truncatedFiles(), context.excludedFiles());
        return new AgentInputSnapshot(context.files(), context.memories(),
                request.systemPrompt(), request.userPrompt(), inputBudget);
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof ProviderCallException provider) return provider.userMessage();
        if (exception instanceof CustomException custom) return custom.errorCode().message();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "AI 실행 중 오류가 발생했습니다.";
        return message.length() <= 1800 ? message : message.substring(0, 1800) + "…";
    }

    private String structureFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "구조화 응답을 해석하지 못했습니다.";
        return message.length() <= 500 ? message : message.substring(0, 500) + "…";
    }

    private void appendOllamaWaitProgress(String projectId, String runId,
                                          GenerationProvider provider,
                                          AgentProgressStage stage) {
        if (!(provider instanceof OllamaGenerationProvider ollama)) return;
        var status = ollama.executionStatus();
        String timeout = status.requestTimeout().toMinutes() > 0
                ? status.requestTimeout().toMinutes() + "분"
                : status.requestTimeout().toSeconds() + "초";
        String message;
        if (status.active() > 0 || status.waiting() > 0) {
            message = "다른 로컬 AI 작업이 실행 중이므로 Ollama 단일 작업 대기열에서 기다리는 중입니다.";
        } else if (!status.warmedUp()) {
            message = "로컬 Ollama 모델을 처음 불러오고 있습니다. 최초 실행은 오래 걸릴 수 있으며 제한 시간은 "
                    + timeout + "입니다.";
        } else {
            message = "로컬 Ollama 실행 슬롯을 확보하고 모델 응답을 기다리는 중입니다.";
        }
        progressService.append(projectId, runId, stage, message);
    }

    private <T> T withProviderRetry(String projectId, AgentRun run, AgentProgressStage stage,
                                    Supplier<T> operation) {
        return retryExecutor.execute(operation,
                () -> cancellationService.checkpoint(projectId, run.getId()),
                notice -> recordProviderRetry(projectId, run, stage, notice));
    }

    private void recordProviderRetry(String projectId, AgentRun run, AgentProgressStage stage,
                                     ProviderRetryExecutor.RetryNotice notice) {
        run.recordProviderRetry();
        runRepository.saveAndFlush(run);
        long seconds = Math.max(1, notice.delay().toSeconds());
        progressService.append(projectId, run.getId(), stage,
                notice.exception().getMessage() + " " + seconds + "초 후 다시 시도합니다. ("
                        + (notice.failedAttempt() + 1) + "/" + notice.maxAttempts() + ")");
    }

    private record DecompositionOutcome(
            TaskDecomposition value,
            GenerationResult generation,
            int attempts,
            String fallbackReason
    ) {
    }
}

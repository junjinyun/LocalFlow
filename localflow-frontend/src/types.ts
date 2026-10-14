export type ApiResponse<T> = { success: boolean; data: T; message: string | null; timestamp: string }

export type Project = {
  id: string
  name: string
  description: string | null
  rootDirectoryName: string | null
  status: 'CREATED' | 'UPLOADING' | 'INDEXING' | 'ANALYZING' | 'READY' | 'FAILED'
  createdAt: string
  updatedAt: string
}

export type ExecutionMode = 'CONFIRM_EVERY_STEP' | 'BALANCED' | 'AUTONOMOUS'
export type PermissionPolicy = 'ALLOW' | 'CONFIRM' | 'DENY'
export type PrivacyMode = 'LOCAL_ONLY' | 'EXTERNAL_ALLOWED'

export type ProjectSettings = {
  projectId: string
  executionMode: ExecutionMode
  privacyMode: PrivacyMode
  readPolicy: PermissionPolicy
  createPolicy: PermissionPolicy
  editPolicy: PermissionPolicy
  movePolicy: PermissionPolicy
  deletePolicy: PermissionPolicy
  executePolicy: PermissionPolicy
  excludedPathPatterns: string[]
  excludedExtensions: string[]
  updatedAt: string
}

export type ProjectFile = {
  id: string
  relativePath: string
  fileName: string
  extension: string
  category: 'SOURCE_CODE' | 'TEXT' | 'DOCUMENT' | 'CONFIGURATION'
  language: string | null
  tags: string[]
  automaticTags: string[]
  userAddedTags: string[]
  userExcludedTags: string[]
  sizeBytes: number
  sha256: string
  updatedAt: string
}

export type CodeSymbol = { id: number; name: string; type: string; lineNumber: number }
export type FileContent = { file: ProjectFile; content: string; symbols: CodeSymbol[] }
export type FileAnalysis = {
  file: ProjectFile
  status: 'PENDING' | 'ANALYZED' | 'NO_SYMBOLS' | 'UNSUPPORTED_CONTENT' | 'FAILED'
  role: string | null
  summary: string | null
  message: string | null
  automaticTags: string[]
  effectiveTags: string[]
  userAddedTags: string[]
  userExcludedTags: string[]
  symbols: CodeSymbol[]
  analyzedAt: string | null
}
export type FileSyncItem = {
  relativePath: string
  changeType: 'ADDED' | 'MODIFIED' | 'UNCHANGED' | 'DELETED' | 'IGNORED'
  reason: string | null
}
export type FileSyncResult = {
  added: number; modified: number; unchanged: number; deleted: number; ignored: number
  totalFiles: number; scopedSync: boolean; scopePaths: string[]; changes: FileSyncItem[]
}

export type ChatMessage = {
  id: string; projectId: string; type: 'PROMPT' | 'HANDOFF' | 'NOTE' | 'SYSTEM'
  content: string; createdAt: string
}

export type MemoryType = 'PROJECT_RULE' | 'IMPLEMENTATION_DECISION' | 'OPEN_TASK' | 'ERROR_CONTEXT' | 'NOTE'
export type ProjectMemory = {
  id: string; projectId: string; type: MemoryType; title: string; content: string
  active: boolean; globalScope: boolean; scopeTags: string[]; scopeFilePatterns: string[]
  scopeTaskTypes: string[]; createdAt: string; updatedAt: string
}
export type MemoryCandidate = {
  id: string; projectId: string; sourceType: 'CHAT_MESSAGE' | 'AGENT_RUN'; sourceId: string
  sourceExcerpt: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; type: MemoryType
  title: string; content: string; globalScope: boolean; scopeTags: string[]
  scopeFilePatterns: string[]; scopeTaskTypes: string[]; possibleDuplicateMemoryId: string | null
  possibleDuplicateTitle: string | null; approvedMemoryId: string | null
  createdAt: string; reviewedAt: string | null
}

export type ProviderType = 'JEV' | 'OPENAI' | 'VERTEX_AI' | 'OLLAMA' | 'GEMINI_CLI'
export type ProviderErrorCode =
  | 'NOT_CONFIGURED' | 'AUTHENTICATION_FAILED' | 'PERMISSION_DENIED'
  | 'QUOTA_EXCEEDED' | 'RATE_LIMITED' | 'TIMEOUT' | 'NETWORK_ERROR'
  | 'MODEL_NOT_SUPPORTED' | 'INVALID_RESPONSE' | 'REQUEST_REJECTED'
  | 'SERVICE_UNAVAILABLE' | 'UNKNOWN'
export type Provider = {
  type: ProviderType; role: 'DECISION' | 'GENERATION'; displayName: string; credentialType: string
  implemented: boolean; configured: boolean; reachable: boolean | null; modelInstalled: boolean | null
  available: boolean; statusMessage: string; model: string | null; models: string[]
}

export type FileOperation = {
  id: string
  action: 'CREATE' | 'UPDATE' | 'MOVE' | 'DELETE'
  path: string
  destinationPath: string | null
  content: string | null
}
export type AgentFileChangeSnapshot = {
  operationId: string | null
  action: FileOperation['action']
  path: string
  destinationPath: string | null
  beforeContent: string | null
  afterContent: string | null
}
export type AgentInputSnapshot = {
  contextFiles: string[]
  fileSelections?: Array<{ path: string; score: number; reasons: string[] }>
  memories: Array<{ id: string; title: string }>
  systemPrompt: string
  userPrompt: string
  budget: {
    maxInputChars: number
    usedInputChars: number
    estimatedInputTokens: number
    includedFiles: string[]
    truncatedFiles: string[]
    excludedFiles: Array<{ path: string; reason: string }>
  } | null
}
export type AgentPlan = { summary: string; response: string; operations: FileOperation[] }
export type AgentApplyResult = {
  success: boolean
  appliedOperations: number
  rollbackAttempted: boolean
  rollbackSuccessful: boolean
  recoveryRequiredPaths: string[]
  errorMessage: string | null
}
export type TaskDecomposition = {
  summary: string
  source: 'AI' | 'LOCAL_FALLBACK' | string
  tasks: Array<{
    id: string
    instruction: string
    dependsOn: string[]
    suggestedTags: string[]
    acceptanceCriteria: string[]
  }>
}
export type AgentTaskExecution = {
  taskId: string
  instruction: string
  dependsOn: string[]
  order: number
  status: 'PENDING' | 'RUNNING' | 'WAITING_APPROVAL' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  planJson: string | null
  changesJson: string | null
  resultMessage: string | null
  errorMessage: string | null
}
export type AgentPlanRevision = {
  version: number
  revisionInstruction: string
  planJson: string
  changesJson: string | null
  archivedAt: string
}
export type AgentDecision = {
  actionType: string
  targetPath: string | null
  selectedTags?: string[]
  taskScopes?: Array<{ instruction: string; tags: string[] }>
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  needsHumanApproval: boolean
  model: string
  rawJson: string | null
  fallbackUsed?: boolean
  fallbackReason?: string | null
  fallbackProviderErrorCode?: ProviderErrorCode | null
}

export type ValidationCommandPreview = {
  id: string
  tool: 'GRADLE' | 'NPM'
  type: 'COMPILE' | 'TYPECHECK' | 'LINT' | 'TEST' | 'BUILD'
  workingDirectory: string
  buildFile: string
  displayName: string
  displayCommand: string
  requiresApproval: boolean
}

export type ProjectValidationPreview = {
  projectId: string
  commands: ValidationCommandPreview[]
  notices: string[]
}

export type ValidationCommandResult = {
  commandId: string
  tool: ValidationCommandPreview['tool']
  type: ValidationCommandPreview['type']
  workingDirectory: string
  displayName: string
  displayCommand: string
  status: 'SUCCESS' | 'FAILED' | 'TIMED_OUT'
  exitCode: number | null
  output: string
  outputTruncated: boolean
  durationMillis: number
  startedAt: string
  completedAt: string
}

export type AgentUsageSummary = {
  provider: ProviderType
  model: string | null
  estimatedInputTokens: number | null
  actualInputTokens: number | null
  actualOutputTokens: number | null
  costStatus: 'PRICED' | 'FREE' | 'UNAVAILABLE'
  currency: string
  inputRatePerMillion: number | null
  outputRatePerMillion: number | null
  estimatedCost: number | null
  actualCost: number | null
}

export type AgentRun = {
  runId: string; projectId: string; prompt: string; executionMode: ExecutionMode
  preferredGenerationProvider: ProviderType | null; preferredModel: string | null
  actualGenerationProvider: ProviderType | null
  status: string; notice: string; decisionJson: string | null; planJson: string | null; changesJson: string | null
  decompositionJson: string | null
  inputSnapshotJson: string | null
  applicationResultJson: string | null
  taskExecutionsJson: string | null
  planRevisionsJson: string | null
  validationResultsJson: string | null
  usageSummaryJson: string | null
  resultMessage: string | null; errorMessage: string | null; model: string | null
  providerErrorCode: ProviderErrorCode | null; errorRetryable: boolean | null
  errorRecommendedAction: string | null
  retryOfRunId: string | null; retriedByRunId: string | null
  inputTokens: number | null; outputTokens: number | null
  providerRetryCount: number; lastProviderRetryAt: string | null
  createdAt: string; updatedAt: string; completedAt: string | null
  cancelRequestedAt: string | null; cancelledAt: string | null
}

export type AgentProgressStage =
  | 'PREPARING' | 'DECOMPOSING' | 'DECOMPOSED' | 'DECIDING'
  | 'SELECTING_CONTEXT' | 'PLANNING' | 'VALIDATING'
  | 'WAITING_APPROVAL' | 'APPLYING' | 'COMPLETED' | 'FAILED'
  | 'CANCELLATION_REQUESTED' | 'CANCELLED' | 'ROLLED_BACK' | 'RECOVERY_REQUIRED'

export type AgentRunProgress = {
  id: number
  stage: AgentProgressStage
  message: string
  createdAt: string
}

export type AgentRunSummary = {
  runId: string; projectId: string; prompt: string; executionMode: ExecutionMode
  preferredGenerationProvider: ProviderType | null; preferredModel: string | null
  actualGenerationProvider: ProviderType | null
  status: string; notice: string; resultSummary: string; fileChanges: Array<{
    action: FileOperation['action']; path: string; destinationPath: string | null
  }>
  errorMessage: string | null; model: string | null
  usageSummaryJson: string | null
  providerErrorCode: ProviderErrorCode | null; errorRetryable: boolean | null
  errorRecommendedAction: string | null
  providerRetryCount: number
  retryOfRunId: string | null; retriedByRunId: string | null
  createdAt: string; updatedAt: string; completedAt: string | null
}

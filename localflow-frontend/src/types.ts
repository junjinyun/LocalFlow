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
  sizeBytes: number
  sha256: string
  updatedAt: string
}

export type CodeSymbol = { id: number; name: string; type: string; lineNumber: number }
export type FileContent = { file: ProjectFile; content: string; symbols: CodeSymbol[] }
export type FileSyncItem = {
  relativePath: string
  changeType: 'ADDED' | 'MODIFIED' | 'UNCHANGED' | 'DELETED' | 'IGNORED'
  reason: string | null
}
export type FileSyncResult = {
  added: number; modified: number; unchanged: number; deleted: number; ignored: number
  totalFiles: number; changes: FileSyncItem[]
}

export type ChatMessage = {
  id: string; projectId: string; type: 'PROMPT' | 'HANDOFF' | 'NOTE' | 'SYSTEM'
  content: string; createdAt: string
}

export type MemoryType = 'PROJECT_RULE' | 'IMPLEMENTATION_DECISION' | 'OPEN_TASK' | 'ERROR_CONTEXT' | 'NOTE'
export type ProjectMemory = {
  id: string; projectId: string; type: MemoryType; title: string; content: string
  active: boolean; createdAt: string; updatedAt: string
}

export type ProviderType = 'JEV' | 'OPENAI' | 'VERTEX_AI' | 'OLLAMA' | 'GEMINI_CLI'
export type Provider = {
  type: ProviderType; role: 'DECISION' | 'GENERATION'; displayName: string; credentialType: string
  implemented: boolean; configured: boolean; reachable: boolean | null; modelInstalled: boolean | null
  available: boolean; statusMessage: string; model: string | null; models: string[]
}

export type FileOperation = {
  action: 'CREATE' | 'UPDATE' | 'MOVE' | 'DELETE'
  path: string
  destinationPath: string | null
  content: string | null
}
export type AgentFileChangeSnapshot = {
  action: FileOperation['action']
  path: string
  destinationPath: string | null
  beforeContent: string | null
  afterContent: string | null
}
export type AgentInputSnapshot = {
  contextFiles: string[]
  systemPrompt: string
  userPrompt: string
}
export type AgentPlan = { summary: string; response: string; operations: FileOperation[] }
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
export type AgentDecision = {
  actionType: string
  targetPath: string | null
  selectedTags?: string[]
  taskScopes?: Array<{ instruction: string; tags: string[] }>
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  needsHumanApproval: boolean
  model: string
  rawJson: string | null
}

export type AgentRun = {
  runId: string; projectId: string; prompt: string; executionMode: ExecutionMode
  preferredGenerationProvider: ProviderType | null; preferredModel: string | null
  actualGenerationProvider: ProviderType | null
  status: string; notice: string; decisionJson: string | null; planJson: string | null; changesJson: string | null
  decompositionJson: string | null
  inputSnapshotJson: string | null
  resultMessage: string | null; errorMessage: string | null; model: string | null
  inputTokens: number | null; outputTokens: number | null
  createdAt: string; updatedAt: string; completedAt: string | null
}

export type AgentProgressStage =
  | 'PREPARING' | 'DECOMPOSING' | 'DECOMPOSED' | 'DECIDING'
  | 'SELECTING_CONTEXT' | 'PLANNING' | 'VALIDATING'
  | 'WAITING_APPROVAL' | 'APPLYING' | 'COMPLETED' | 'FAILED'

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
  createdAt: string; updatedAt: string; completedAt: string | null
}

import type {
  AgentRun, AgentRunProgress, AgentRunSummary, ApiResponse, ChatMessage, ExecutionMode, FileContent, FileSyncResult,
  MemoryType, Project, ProjectFile, ProjectMemory, ProjectSettings, Provider, ProviderType,
} from './types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const TERMINAL_RUN_STATUSES = new Set(['WAITING_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED'])

const wait = (milliseconds: number) => new Promise(resolve => window.setTimeout(resolve, milliseconds))

export class ApiError extends Error {
  code?: string
  constructor(message: string, code?: string) {
    super(message)
    this.code = code
  }
}

function apiError(body: any, status: number) {
  const validation = body?.errors
    ?.map((item: { field: string; message: string }) => `${item.field}: ${item.message}`)
    .join('\n')
  return new ApiError(validation || body?.message || `요청 처리에 실패했습니다. (${status})`, body?.code)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: init?.body instanceof FormData
      ? init.headers
      : { 'Content-Type': 'application/json', ...init?.headers },
  })
  const body = await response.json().catch(() => null)
  if (!response.ok || !body?.success) {
    throw apiError(body, response.status)
  }
  return (body as ApiResponse<T>).data
}

export const api = {
  projects: {
    list: () => request<Project[]>('/api/projects'),
    get: (id: string) => request<Project>(`/api/projects/${id}`),
    create: (name: string, description: string) => request<Project>('/api/projects', {
      method: 'POST', body: JSON.stringify({ name, description: description || null }),
    }),
    update: (id: string, name: string, description: string) => request<Project>(`/api/projects/${id}`, {
      method: 'PUT', body: JSON.stringify({ name, description: description || null }),
    }),
    remove: (id: string) => request<void>(`/api/projects/${id}`, { method: 'DELETE' }),
    settings: (id: string) => request<ProjectSettings>(`/api/projects/${id}/settings`),
    updateSettings: (id: string, settings: Omit<ProjectSettings, 'projectId' | 'updatedAt'>) =>
      request<ProjectSettings>(`/api/projects/${id}/settings`, { method: 'PUT', body: JSON.stringify(settings) }),
  },
  files: {
    list: (projectId: string) => request<ProjectFile[]>(`/api/projects/${projectId}/files`),
    content: (projectId: string, fileId: string) => request<FileContent>(`/api/projects/${projectId}/files/${fileId}/content`),
    sync: (projectId: string, files: File[], fullSync: boolean, scope: string,
      onProgress?: (percent: number) => void) => {
      const form = new FormData()
      const relativePaths = files.map(file => file.webkitRelativePath || file.name)
      files.forEach(file => form.append('files', file, file.name))
      form.append('manifest', new Blob(
        [JSON.stringify({ relativePaths })],
        { type: 'application/json' },
      ))
      const query = new URLSearchParams({ fullSync: String(fullSync) })
      if (scope.trim()) query.set('scope', scope.trim())
      return new Promise<FileSyncResult>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', `${API_BASE}/api/projects/${projectId}/files/sync?${query}`)
        xhr.responseType = 'json'
        xhr.upload.onprogress = event => {
          if (event.lengthComputable) onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)))
        }
        xhr.onload = () => {
          const body = xhr.response
          if (xhr.status >= 200 && xhr.status < 300 && body?.success) {
            onProgress?.(100)
            resolve((body as ApiResponse<FileSyncResult>).data)
          } else {
            reject(apiError(body, xhr.status))
          }
        }
        xhr.onerror = () => reject(new ApiError('백엔드 서버와 연결할 수 없습니다.'))
        xhr.onabort = () => reject(new ApiError('파일 업로드가 취소되었습니다.'))
        xhr.send(form)
      })
    },
    write: (projectId: string, relativePath: string, content: string, fileId?: string) =>
      request<ProjectFile>(`/api/projects/${projectId}/files${fileId ? `/${fileId}` : ''}`, {
        method: fileId ? 'PUT' : 'POST', body: JSON.stringify({ relativePath, content, approved: true }),
      }),
    move: (projectId: string, fileId: string, destinationPath: string) =>
      request<ProjectFile>(`/api/projects/${projectId}/files/${fileId}/move`, {
        method: 'POST', body: JSON.stringify({ destinationPath, approved: true }),
      }),
    remove: (projectId: string, fileId: string) =>
      request<void>(`/api/projects/${projectId}/files/${fileId}?approved=true`, { method: 'DELETE' }),
    downloadUrl: (projectId: string, fileId: string) => `${API_BASE}/api/projects/${projectId}/files/${fileId}/download`,
    archiveUrl: (projectId: string) => `${API_BASE}/api/projects/${projectId}/archive`,
  },
  chat: {
    list: (projectId: string) => request<ChatMessage[]>(`/api/projects/${projectId}/messages`),
    create: (projectId: string, type: ChatMessage['type'], content: string) =>
      request<ChatMessage>(`/api/projects/${projectId}/messages`, { method: 'POST', body: JSON.stringify({ type, content }) }),
    remove: (projectId: string, messageId: string) =>
      request<void>(`/api/projects/${projectId}/messages/${messageId}`, { method: 'DELETE' }),
  },
  memories: {
    list: (projectId: string, activeOnly = false) => request<ProjectMemory[]>(
      `/api/projects/${projectId}/memories?activeOnly=${activeOnly}`,
    ),
    create: (projectId: string, type: MemoryType, title: string, content: string) =>
      request<ProjectMemory>(`/api/projects/${projectId}/memories`, {
        method: 'POST', body: JSON.stringify({ type, title, content }),
      }),
    update: (projectId: string, memory: ProjectMemory) => request<ProjectMemory>(
      `/api/projects/${projectId}/memories/${memory.id}`,
      { method: 'PUT', body: JSON.stringify({ type: memory.type, title: memory.title, content: memory.content, active: memory.active }) },
    ),
    remove: (projectId: string, memoryId: string) =>
      request<void>(`/api/projects/${projectId}/memories/${memoryId}`, { method: 'DELETE' }),
  },
  runs: {
    list: (projectId: string) => request<AgentRunSummary[]>(`/api/projects/${projectId}/agent-runs`),
    get: (projectId: string, runId: string) =>
      request<AgentRun>(`/api/projects/${projectId}/agent-runs/${runId}`),
    progress: (projectId: string, runId: string) =>
      request<AgentRunProgress[]>(`/api/projects/${projectId}/agent-runs/${runId}/progress`),
    create: (projectId: string, prompt: string, executionMode: ExecutionMode, provider: ProviderType | null,
      model: string | null) =>
      request<AgentRun>(`/api/projects/${projectId}/agent-runs`, {
        method: 'POST',
        body: JSON.stringify({ prompt, executionMode, preferredGenerationProvider: provider, preferredModel: model }),
      }),
    cancel: (projectId: string, runId: string) =>
      request<AgentRun>(`/api/projects/${projectId}/agent-runs/${runId}/cancel`, { method: 'POST' }),
    execute: (projectId: string, runId: string, approved = false) =>
      request<AgentRun>(`/api/projects/${projectId}/agent-runs/${runId}/execute`, {
        method: 'POST', body: JSON.stringify({ approved }),
      }),
    waitForCompletion: async (projectId: string, runId: string,
      onUpdate?: (run: AgentRun) => void) => {
      while (true) {
        const run = await request<AgentRun>(`/api/projects/${projectId}/agent-runs/${runId}`)
        onUpdate?.(run)
        if (TERMINAL_RUN_STATUSES.has(run.status)) return run
        await wait(700)
      }
    },
  },
  providers: (refresh = false) => request<Provider[]>(`/api/ai/providers?refresh=${refresh}`),
  health: () => request<{ status: string; service: string }>('/api/health'),
}

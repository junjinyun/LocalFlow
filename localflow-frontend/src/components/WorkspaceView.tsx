import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import {
  Check, ChevronDown, ChevronRight, Code2, Download, File, FilePlus2, FileText, Folder, FolderOpen,
  GitCompareArrows, LoaderCircle, Move, RefreshCw, Save, Search, Trash2, UploadCloud,
} from 'lucide-react'
import { api } from '../api'
import type { FileContent, FileSyncResult, Project, ProjectFile } from '../types'
import Modal from './Modal'

type Props = {
  project: Project
  onProjectRefresh: () => Promise<void> | void
  notify: (message: string, tone?: 'success' | 'error') => void
}

type SelectionStats = {
  selected: number
  accepted: number
  ignoredDirectories: number
  unsupported: number
  oversized: number
}

type FileTreeItem = {
  id: string
  name: string
  path: string
  kind: 'directory' | 'file'
  file?: ProjectFile
  children?: FileTreeItem[]
}

type MutableDirectory = {
  name: string
  path: string
  directories: Map<string, MutableDirectory>
  files: ProjectFile[]
}

const MAX_FILES = 5000
const MAX_FILE_BYTES = 30 * 1024 * 1024
const MAX_PROJECT_BYTES = 250 * 1024 * 1024
const MIN_FILE_PANEL_WIDTH = 220
const MAX_FILE_PANEL_WIDTH = 560
const ignoredDirectories = new Set([
  '.git', '.gradle', '.idea', '.venv', 'build', 'coverage', 'dist', '.next', '.nuxt',
  '.turbo', '.cache', 'node_modules', 'out', 'target', 'vendor', 'venv',
])
const supportedExtensions = new Set([
  'java', 'kt', 'kts', 'py', 'js', 'jsx', 'ts', 'tsx', 'c', 'h', 'cpp', 'hpp',
  'cs', 'go', 'rs', 'php', 'rb', 'swift', 'scala', 'vue', 'svelte', 'html', 'css',
  'scss', 'sql', 'sh', 'ps1', 'txt', 'md', 'adoc', 'csv', 'tsv', 'log', 'json',
  'yml', 'yaml', 'xml', 'toml', 'ini', 'conf', 'properties', 'gradle', 'doc', 'docx',
  'hwp', 'hwpx', 'rtf',
])
const sensitiveNames = new Set(['.env', '.env.local', '.env.production', 'id_rsa', 'id_ed25519'])

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`
}

const changeLabels: Record<FileSyncResult['changes'][number]['changeType'], string> = {
  ADDED: '추가', MODIFIED: '수정', UNCHANGED: '유지', DELETED: '삭제', IGNORED: '제외',
}

const FileGlyph = ({ category }: { category: ProjectFile['category'] }) => {
  if (category === 'SOURCE_CODE') return <Code2 size={16} />
  if (category === 'DOCUMENT') return <FileText size={16} />
  return <File size={16} />
}

const compareNames = (left: string, right: string) => left.localeCompare(right, 'ko', { numeric: true, sensitivity: 'base' })

const buildFileTree = (files: ProjectFile[]): FileTreeItem[] => {
  const root: MutableDirectory = { name: '', path: '', directories: new Map(), files: [] }

  for (const file of files) {
    const segments = file.relativePath.replaceAll('\\', '/').split('/').filter(Boolean)
    const fileName = segments.pop() || file.fileName
    let current = root

    for (const segment of segments) {
      const path = current.path ? `${current.path}/${segment}` : segment
      let child = current.directories.get(segment)
      if (!child) {
        child = { name: segment, path, directories: new Map(), files: [] }
        current.directories.set(segment, child)
      }
      current = child
    }

    current.files.push({ ...file, fileName })
  }

  const toItems = (directory: MutableDirectory): FileTreeItem[] => [
    ...Array.from(directory.directories.values())
      .sort((left, right) => compareNames(left.name, right.name))
      .map(child => ({
        id: `directory:${child.path}`,
        name: child.name,
        path: child.path,
        kind: 'directory' as const,
        children: toItems(child),
      })),
    ...directory.files
      .sort((left, right) => compareNames(left.fileName, right.fileName))
      .map(file => ({
        id: `file:${file.id}`,
        name: file.fileName,
        path: file.relativePath,
        kind: 'file' as const,
        file,
      })),
  ]

  return toItems(root)
}

const directoryPaths = (relativePath: string) => {
  const segments = relativePath.replaceAll('\\', '/').split('/').filter(Boolean)
  return segments.slice(0, -1).map((_, index) => segments.slice(0, index + 1).join('/'))
}

type FileTreeProps = {
  items: FileTreeItem[]
  selectedId: string | null
  expandedDirectories: Set<string>
  searchActive: boolean
  onToggle: (path: string) => void
  onSelect: (id: string) => void
  depth?: number
}

const FileTree = ({
  items, selectedId, expandedDirectories, searchActive, onToggle, onSelect, depth = 0,
}: FileTreeProps) => (
  <>
    {items.map(item => {
      if (item.kind === 'directory') {
        const expanded = searchActive || expandedDirectories.has(item.path)
        return (
          <div className="file-tree-branch" key={item.id}>
            <button
              className="tree-row directory-row"
              style={{ paddingLeft: 8 + depth * 15 }}
              onClick={() => onToggle(item.path)}
              aria-expanded={expanded}
              title={item.path}
            >
              {expanded ? <ChevronDown className="tree-disclosure" size={14} /> : <ChevronRight className="tree-disclosure" size={14} />}
              {expanded ? <FolderOpen className="tree-folder" size={16} /> : <Folder className="tree-folder" size={16} />}
              <span>{item.name}</span>
            </button>
            {expanded && item.children && (
              <FileTree
                items={item.children}
                selectedId={selectedId}
                expandedDirectories={expandedDirectories}
                searchActive={searchActive}
                onToggle={onToggle}
                onSelect={onSelect}
                depth={depth + 1}
              />
            )}
          </div>
        )
      }

      const file = item.file
      if (!file) return null
      return (
        <button
          key={item.id}
          className={`tree-row file-row ${selectedId === file.id ? 'active' : ''}`}
          style={{ paddingLeft: 8 + depth * 15 }}
          onClick={() => onSelect(file.id)}
          title={item.path}
        >
          <span className="tree-spacer" />
          <FileGlyph category={file.category} />
          <span>{item.name}</span>
          <small>{formatBytes(file.sizeBytes)}</small>
        </button>
      )
    })}
  </>
)

export default function WorkspaceView({ project, onProjectRefresh, notify }: Props) {
  const [files, setFiles] = useState<ProjectFile[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [fileContent, setFileContent] = useState<FileContent | null>(null)
  const [editor, setEditor] = useState('')
  const [search, setSearch] = useState('')
  const [expandedDirectories, setExpandedDirectories] = useState<Set<string>>(new Set())
  const [filePanelWidth, setFilePanelWidth] = useState(() => {
    const saved = Number(window.localStorage.getItem('localflow.filePanelWidth'))
    return Number.isFinite(saved) ? Math.min(MAX_FILE_PANEL_WIDTH, Math.max(MIN_FILE_PANEL_WIDTH, saved)) : 295
  })
  const [resizingFilePanel, setResizingFilePanel] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [pendingFiles, setPendingFiles] = useState<File[]>([])
  const [selectionStats, setSelectionStats] = useState<SelectionStats | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [syncElapsed, setSyncElapsed] = useState(0)
  const [fullSync, setFullSync] = useState(true)
  const [scope, setScope] = useState('')
  const [syncResult, setSyncResult] = useState<FileSyncResult | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [newPath, setNewPath] = useState('')
  const [newContent, setNewContent] = useState('')
  const directoryInput = useRef<HTMLInputElement>(null)
  const editorInput = useRef<HTMLTextAreaElement>(null)
  const workspaceGrid = useRef<HTMLElement>(null)
  const resizeCleanup = useRef<(() => void) | null>(null)

  const selected = files.find(file => file.id === selectedId) ?? null
  const dirty = fileContent ? editor !== fileContent.content : false
  const normalizedSearch = search.trim().toLowerCase()
  const visibleFiles = useMemo(
    () => normalizedSearch ? files.filter(file => file.relativePath.toLowerCase().includes(normalizedSearch)) : files,
    [files, normalizedSearch],
  )
  const fileTree = useMemo(() => buildFileTree(visibleFiles), [visibleFiles])

  const loadFiles = async (keepSelection = true) => {
    setLoading(true)
    try {
      const data = await api.files.list(project.id)
      setFiles(data)
      if (!keepSelection) {
        setExpandedDirectories(new Set(data.flatMap(file => {
          const segments = file.relativePath.replaceAll('\\', '/').split('/').filter(Boolean)
          return segments.length > 1 ? [segments[0]] : []
        })))
      }
      setSelectedId(current => keepSelection && current && data.some(file => file.id === current) ? current : data[0]?.id ?? null)
    } catch (error) {
      notify(error instanceof Error ? error.message : '파일 목록을 불러오지 못했습니다.', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setSelectedId(null)
    setFileContent(null)
    setSyncResult(null)
    setSelectionStats(null)
    setSearch('')
    setExpandedDirectories(new Set())
    loadFiles(false)
  }, [project.id])

  useEffect(() => {
    window.localStorage.setItem('localflow.filePanelWidth', String(filePanelWidth))
  }, [filePanelWidth])

  useEffect(() => () => resizeCleanup.current?.(), [])

  useEffect(() => {
    if (!selected) return
    const paths = directoryPaths(selected.relativePath)
    if (!paths.length) return
    setExpandedDirectories(current => {
      const next = new Set(current)
      let changed = false
      for (const path of paths) {
        if (!next.has(path)) {
          next.add(path)
          changed = true
        }
      }
      return changed ? next : current
    })
  }, [selected?.relativePath])

  const toggleDirectory = (path: string) => {
    if (normalizedSearch) return
    setExpandedDirectories(current => {
      const next = new Set(current)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })
  }

  const maxFilePanelWidth = () => {
    const availableWidth = workspaceGrid.current?.getBoundingClientRect().width ?? window.innerWidth
    return Math.max(MIN_FILE_PANEL_WIDTH, Math.min(MAX_FILE_PANEL_WIDTH, availableWidth - 360))
  }

  const startFilePanelResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (window.matchMedia('(max-width: 800px)').matches) return
    event.preventDefault()
    const startX = event.clientX
    const startWidth = filePanelWidth
    setResizingFilePanel(true)
    document.body.classList.add('resizing-file-panel')

    const handleMove = (moveEvent: PointerEvent) => {
      const nextWidth = startWidth + moveEvent.clientX - startX
      setFilePanelWidth(Math.min(maxFilePanelWidth(), Math.max(MIN_FILE_PANEL_WIDTH, nextWidth)))
    }
    const finish = () => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', finish)
      window.removeEventListener('pointercancel', finish)
      document.body.classList.remove('resizing-file-panel')
      setResizingFilePanel(false)
      resizeCleanup.current = null
    }

    resizeCleanup.current?.()
    resizeCleanup.current = finish
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', finish)
    window.addEventListener('pointercancel', finish)
  }

  const resizeFilePanelWithKeyboard = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const direction = event.key === 'ArrowRight' ? 1 : -1
    setFilePanelWidth(current => Math.min(maxFilePanelWidth(), Math.max(MIN_FILE_PANEL_WIDTH, current + direction * 20)))
  }

  useEffect(() => {
    if (!syncing) return
    const startedAt = Date.now()
    setSyncElapsed(0)
    const timer = window.setInterval(() => setSyncElapsed(Math.floor((Date.now() - startedAt) / 1000)), 1000)
    return () => window.clearInterval(timer)
  }, [syncing])

  const bindDirectoryInput = (input: HTMLInputElement | null) => {
    directoryInput.current = input
    if (input) {
      input.setAttribute('webkitdirectory', '')
      input.setAttribute('directory', '')
    }
  }

  const selectDirectory = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files ?? [])
    const accepted: File[] = []
    let ignoredDirectoryCount = 0
    let unsupportedCount = 0
    let oversizedCount = 0

    for (const file of selectedFiles) {
      const relativePath = (file.webkitRelativePath || file.name).replaceAll('\\', '/')
      const segments = relativePath.toLowerCase().split('/')
      const fileName = segments.at(-1) ?? ''
      if (segments.some(segment => ignoredDirectories.has(segment))) {
        ignoredDirectoryCount++
        continue
      }
      if (sensitiveNames.has(fileName) || fileName.endsWith('.pem') || fileName.endsWith('.key')
        || fileName.endsWith('.p12') || (fileName.includes('service-account') && fileName.endsWith('.json'))) {
        unsupportedCount++
        continue
      }
      const dot = fileName.lastIndexOf('.')
      const extension = dot < 0 ? '' : fileName.slice(dot + 1)
      if (extension && !supportedExtensions.has(extension)) {
        unsupportedCount++
        continue
      }
      if (file.size > MAX_FILE_BYTES) {
        oversizedCount++
        continue
      }
      accepted.push(file)
    }

    const stats = {
      selected: selectedFiles.length,
      accepted: accepted.length,
      ignoredDirectories: ignoredDirectoryCount,
      unsupported: unsupportedCount,
      oversized: oversizedCount,
    }
    setSelectionStats(stats)
    const totalBytes = accepted.reduce((total, file) => total + file.size, 0)
    if (accepted.length > MAX_FILES) {
      setPendingFiles([])
      event.target.value = ''
      notify(`제외 폴더를 정리한 뒤에도 ${accepted.length.toLocaleString()}개입니다. 한 프로젝트는 최대 ${MAX_FILES.toLocaleString()}개 파일까지 업로드할 수 있습니다.`, 'error')
      return
    }
    if (totalBytes > MAX_PROJECT_BYTES) {
      setPendingFiles([])
      event.target.value = ''
      notify(`업로드 대상 용량이 ${formatBytes(totalBytes)}입니다. 프로젝트 최대 용량은 ${formatBytes(MAX_PROJECT_BYTES)}입니다.`, 'error')
      return
    }
    setPendingFiles(accepted)
    const ignored = selectedFiles.length - accepted.length
    if (!accepted.length) notify('업로드 가능한 코드·텍스트·문서 파일이 없습니다.', 'error')
    else if (ignored > 0) notify(`${accepted.length.toLocaleString()}개 파일을 준비하고 의존성·바이너리 ${ignored.toLocaleString()}개를 제외했습니다.`)
  }

  useEffect(() => {
    if (!selected) {
      setFileContent(null)
      setEditor('')
      return
    }
    if (selected.category === 'DOCUMENT') {
      setFileContent(null)
      setEditor('')
      return
    }
    api.files.content(project.id, selected.id)
      .then(content => { setFileContent(content); setEditor(content.content) })
      .catch(error => notify(error instanceof Error ? error.message : '파일을 열지 못했습니다.', 'error'))
  }, [project.id, selectedId])

  const syncDirectory = async () => {
    if (!pendingFiles.length) return
    setUploadProgress(0)
    setSyncing(true)
    try {
      const result = await api.files.sync(project.id, pendingFiles, fullSync, scope, setUploadProgress)
      setSyncResult(result)
      setPendingFiles([])
      setSelectionStats(null)
      if (directoryInput.current) directoryInput.current.value = ''
      await loadFiles(false)
      await onProjectRefresh()
      notify('디렉터리 동기화를 완료했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '동기화에 실패했습니다.', 'error')
    } finally {
      setSyncing(false)
    }
  }

  const saveCurrent = async () => {
    if (!selected || !fileContent) return
    setSaving(true)
    try {
      const saved = await api.files.write(project.id, selected.relativePath, editor, selected.id)
      setFiles(current => current.map(file => file.id === saved.id ? saved : file))
      setFileContent({ ...fileContent, file: saved, content: editor })
      notify('파일을 저장했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '파일 저장에 실패했습니다.', 'error')
    } finally {
      setSaving(false)
    }
  }

  const createFile = async (event: React.FormEvent) => {
    event.preventDefault()
    try {
      const created = await api.files.write(project.id, newPath.trim(), newContent)
      await loadFiles(false)
      setSelectedId(created.id)
      setCreateOpen(false)
      setNewPath('')
      setNewContent('')
      notify('새 파일을 만들었습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '파일 생성에 실패했습니다.', 'error')
    }
  }

  const moveFile = async () => {
    if (!selected) return
    const destination = window.prompt('이동할 상대 경로를 입력하세요.', selected.relativePath)
    if (!destination || destination === selected.relativePath) return
    try {
      const moved = await api.files.move(project.id, selected.id, destination)
      await loadFiles(false)
      setSelectedId(moved.id)
      notify('파일 경로를 변경했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '파일 이동에 실패했습니다.', 'error')
    }
  }

  const deleteFile = async () => {
    if (!selected || !window.confirm(`${selected.relativePath} 파일을 삭제할까요?`)) return
    try {
      await api.files.remove(project.id, selected.id)
      await loadFiles(false)
      notify('파일을 삭제했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '파일 삭제에 실패했습니다.', 'error')
    }
  }

  const goToLine = (lineNumber: number) => {
    const input = editorInput.current
    if (!input) return
    const lines = editor.split('\n')
    const offset = lines.slice(0, Math.max(0, lineNumber - 1))
      .reduce((total, line) => total + line.length + 1, 0)
    input.focus()
    input.setSelectionRange(offset, Math.min(offset + (lines[lineNumber - 1]?.length ?? 0), editor.length))
    const lineHeight = Number.parseFloat(window.getComputedStyle(input).lineHeight) || 20
    input.scrollTop = Math.max(0, (lineNumber - 3) * lineHeight)
  }

  const pendingBytes = pendingFiles.reduce((total, file) => total + file.size, 0)
  const processing = uploadProgress >= 100
  const uploadOverlay = syncing && (
    <div className="upload-progress-backdrop" role="status" aria-live="polite" aria-busy="true">
      <div className="upload-progress-card">
        <div className="upload-progress-icon"><UploadCloud size={28} /><span /></div>
        <p className="eyebrow">PROJECT SYNC</p>
        <h2>{processing ? '서버에서 파일을 분석하고 있습니다.' : '프로젝트를 업로드하고 있습니다.'}</h2>
        <p>{processing
          ? '변경 사항 확인과 코드 인덱싱을 진행 중입니다. 파일이 많으면 잠시 시간이 걸릴 수 있습니다.'
          : '선택한 디렉터리를 백엔드 작업공간으로 안전하게 전송하고 있습니다.'}</p>
        <div className={`upload-progress-track ${processing ? 'processing' : ''}`}>
          <span style={{ width: `${uploadProgress}%` }} />
        </div>
        <div className="upload-progress-meta">
          <strong>{processing ? '분석 중' : `${uploadProgress}%`}</strong>
          <span>{pendingFiles.length.toLocaleString()}개 파일 · {formatBytes(pendingBytes)}</span>
          <time>{syncElapsed}초 경과</time>
        </div>
        <div className="upload-progress-steps">
          <span className="done"><Check size={13} />파일 선택</span>
          <span className={processing ? 'done' : 'active'}>{processing ? <Check size={13} /> : <LoaderCircle className="spin" size={13} />}업로드</span>
          <span className={processing ? 'active' : ''}><LoaderCircle className={processing ? 'spin' : ''} size={13} />분석·인덱싱</span>
        </div>
        <small>완료될 때까지 이 화면을 닫지 마세요.</small>
      </div>
    </div>
  )

  if (loading && files.length === 0) return <div className="center-state"><LoaderCircle className="spin" /><p>작업공간을 불러오는 중입니다.</p></div>

  if (files.length === 0) {
    return (
      <>{uploadOverlay}<section className="onboarding-card">
        <span className="large-icon"><FolderOpen size={30} /></span>
        <p className="eyebrow">첫 번째 단계</p>
        <h2>프로젝트 디렉터리를 연결하세요.</h2>
        <p>원본 PC 파일을 직접 건드리지 않고, 업로드된 사본에서만 작업합니다. 무거운 의존성 폴더와 바이너리는 자동으로 제외됩니다.</p>
        <input ref={bindDirectoryInput} className="hidden-input" type="file" multiple onChange={selectDirectory} />
        <button className="upload-zone" onClick={() => directoryInput.current?.click()}>
          <UploadCloud size={25} />
          {pendingFiles.length ? <><strong>{pendingFiles.length.toLocaleString()}개 파일 업로드 준비됨</strong><small>{selectionStats && selectionStats.selected !== selectionStats.accepted ? `전체 ${selectionStats.selected.toLocaleString()}개 중 ${(selectionStats.selected - selectionStats.accepted).toLocaleString()}개 자동 제외` : pendingFiles[0]?.webkitRelativePath.split('/')[0] || '선택한 파일'}</small></> : <><strong>디렉터리 선택</strong><small>코드, 텍스트, Word/HWP 파일</small></>}
        </button>
        {pendingFiles.length > 0 && <button className="primary-button" onClick={syncDirectory} disabled={syncing}>{syncing ? '인덱싱 중…' : '업로드하고 인덱싱'}</button>}
      </section></>
    )
  }

  return (
    <>{uploadOverlay}<section
      ref={workspaceGrid}
      className={`workspace-grid ${resizingFilePanel ? 'resizing' : ''}`}
      style={{ '--file-panel-width': `${filePanelWidth}px` } as CSSProperties}
    >
      <aside className="file-panel panel">
        <div className="panel-header">
          <div><p className="eyebrow">FILES</p><h2>{project.rootDirectoryName || '프로젝트 파일'}</h2></div>
          <div className="inline-actions">
            <button className="icon-button" onClick={() => setCreateOpen(true)} title="파일 생성"><FilePlus2 size={17} /></button>
            <button className="icon-button" onClick={() => directoryInput.current?.click()} title="다시 동기화"><RefreshCw size={17} /></button>
          </div>
        </div>
        <input ref={bindDirectoryInput} className="hidden-input" type="file" multiple onChange={selectDirectory} />
        {pendingFiles.length > 0 && (
          <div className="sync-box">
            <strong>{pendingFiles.length}개 파일 준비됨</strong>
            {selectionStats && selectionStats.selected !== selectionStats.accepted && <small className="selection-filter-summary">전체 {selectionStats.selected.toLocaleString()}개 중 폴더 제외 {selectionStats.ignoredDirectories.toLocaleString()} · 형식/민감 파일 제외 {selectionStats.unsupported.toLocaleString()} · 대용량 제외 {selectionStats.oversized.toLocaleString()}</small>}
            <label className="check-row"><input type="checkbox" checked={fullSync} onChange={event => setFullSync(event.target.checked)} />누락 파일 삭제</label>
            <input value={scope} onChange={event => setScope(event.target.value)} placeholder="선택 범위 (선택사항)" />
            <div className="inline-actions"><button className="small-button" onClick={() => { setPendingFiles([]); setSelectionStats(null); if (directoryInput.current) directoryInput.current.value = '' }}>취소</button><button className="small-button accent" onClick={syncDirectory} disabled={syncing}>{syncing ? '동기화 중…' : '동기화'}</button></div>
          </div>
        )}
        {syncResult && (
          <div className="sync-result">
            <button className="sync-summary" onClick={() => setSyncResult(null)} title="결과 닫기">
              <Check size={15} /><span>추가 {syncResult.added} · 수정 {syncResult.modified} · 삭제 {syncResult.deleted} · 제외 {syncResult.ignored}</span>
            </button>
            <div className="sync-change-list">{syncResult.changes.slice(0, 20).map((change, index) => <div key={`${change.relativePath}-${index}`}><b className={change.changeType.toLowerCase()}>{changeLabels[change.changeType]}</b><span title={change.relativePath}>{change.relativePath}</span>{change.reason && <small>{change.reason}</small>}</div>)}{syncResult.changes.length > 20 && <p>외 {syncResult.changes.length - 20}개 변경</p>}</div>
          </div>
        )}
        <label className="search-box"><Search size={15} /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="파일 검색" /></label>
        <div className="file-list">
          {fileTree.length > 0 ? (
            <FileTree
              items={fileTree}
              selectedId={selectedId}
              expandedDirectories={expandedDirectories}
              searchActive={Boolean(normalizedSearch)}
              onToggle={toggleDirectory}
              onSelect={setSelectedId}
            />
          ) : <div className="file-tree-empty">검색 결과가 없습니다.</div>}
        </div>
        <footer>{normalizedSearch ? `${visibleFiles.length} / ${files.length}개 파일` : `${files.length}개 파일`} · 이미지와 의존성 폴더 제외</footer>
      </aside>

      <div
        className="file-panel-resizer"
        role="separator"
        aria-label="파일 목록 너비 조절"
        aria-orientation="vertical"
        aria-valuemin={MIN_FILE_PANEL_WIDTH}
        aria-valuemax={MAX_FILE_PANEL_WIDTH}
        aria-valuenow={Math.round(filePanelWidth)}
        tabIndex={0}
        title="드래그하여 파일 목록 너비 조절"
        onPointerDown={startFilePanelResize}
        onKeyDown={resizeFilePanelWithKeyboard}
      />

      <div className="editor-panel panel">
        {selected ? (
          <>
            <div className="editor-toolbar">
              <div className="breadcrumb"><span>{project.name}</span><ChevronRight size={14} /><strong>{selected.relativePath}</strong>{dirty && <i>수정됨</i>}</div>
              <div className="inline-actions">
                <a className="icon-button" href={api.files.downloadUrl(project.id, selected.id)} title="다운로드"><Download size={17} /></a>
                <button className="icon-button" onClick={moveFile} title="이동"><Move size={17} /></button>
                <button className="icon-button danger" onClick={deleteFile} title="삭제"><Trash2 size={17} /></button>
                {fileContent && <button className="small-button accent" onClick={saveCurrent} disabled={!dirty || saving}><Save size={15} />{saving ? '저장 중' : '저장'}</button>}
              </div>
            </div>
            {selected.category === 'DOCUMENT' ? (
              <div className="document-preview"><FileText size={38} /><h3>{selected.fileName}</h3><p>문서 본문 미리보기는 확장 기능입니다. 현재는 원본 저장과 다운로드를 지원합니다.</p><a className="primary-button" href={api.files.downloadUrl(project.id, selected.id)}>원본 다운로드</a></div>
            ) : (
              <div className="editor-body">
                <textarea ref={editorInput} className="code-editor" spellCheck={false} value={editor} onChange={event => setEditor(event.target.value)} />
                <aside className="symbol-panel">
                  <div><GitCompareArrows size={15} /><strong>파일 인덱스</strong></div>
                  <dl><dt>언어</dt><dd>{selected.language || selected.category}</dd><dt>크기</dt><dd>{formatBytes(selected.sizeBytes)}</dd><dt>해시</dt><dd title={selected.sha256}>{selected.sha256.slice(0, 10)}…</dd></dl>
                  <h4>심볼 {fileContent?.symbols.length ?? 0}</h4>
                  {fileContent?.symbols.map(symbol => <button key={symbol.id} onClick={() => goToLine(symbol.lineNumber)}><Code2 size={13} /><span>{symbol.name}</span><small>L{symbol.lineNumber}</small></button>)}
                </aside>
              </div>
            )}
          </>
        ) : <div className="center-state"><FileText /><p>왼쪽에서 파일을 선택하세요.</p></div>}
      </div>

      {createOpen && (
        <Modal title="새 텍스트 파일" onClose={() => setCreateOpen(false)}>
          <form className="form-stack" onSubmit={createFile}>
            <label>상대 경로<input autoFocus value={newPath} onChange={event => setNewPath(event.target.value)} placeholder="src/main/java/example/NewFile.java" /></label>
            <label>초기 내용<textarea className="tall" value={newContent} onChange={event => setNewContent(event.target.value)} placeholder="파일 내용을 입력하세요." /></label>
            <div className="modal-actions"><button type="button" className="ghost-button" onClick={() => setCreateOpen(false)}>취소</button><button className="primary-button" disabled={!newPath.trim()}>파일 생성</button></div>
          </form>
        </Modal>
      )}
    </section></>
  )
}

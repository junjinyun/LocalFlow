import { useEffect, useMemo, useState } from 'react'
import { Bot, Brain, Files, PlaySquare, Settings, Sparkles } from 'lucide-react'
import { api } from './api'
import type { Project, Provider } from './types'
import Sidebar from './components/Sidebar'
import Modal from './components/Modal'
import WorkspaceView from './components/WorkspaceView'
import ChatView from './components/ChatView'
import MemoryView from './components/MemoryView'
import SettingsView from './components/SettingsView'
import RunsView from './components/RunsView'

type Tab = 'workspace' | 'chat' | 'memory' | 'settings' | 'runs'
const tabs: { key: Tab; label: string; icon: typeof Files }[] = [
  { key: 'workspace', label: '작업공간', icon: Files },
  { key: 'chat', label: '대화', icon: Bot },
  { key: 'memory', label: '기억', icon: Brain },
  { key: 'runs', label: '실행 기록', icon: PlaySquare },
  { key: 'settings', label: '설정', icon: Settings },
]

export default function App() {
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('workspace')
  const [online, setOnline] = useState(false)
  const [providers, setProviders] = useState<Provider[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState<{ message: string; tone: 'success' | 'error' } | null>(null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  const selected = useMemo(() => projects.find(project => project.id === selectedId) ?? null, [projects, selectedId])

  const notify = (message: string, tone: 'success' | 'error' = 'success') => {
    setToast({ message, tone })
    window.setTimeout(() => setToast(current => current?.message === message ? null : current), 3200)
  }

  const loadProjects = async (preferId?: string) => {
    const data = await api.projects.list()
    setProjects(data)
    setSelectedId(current => preferId ?? (current && data.some(item => item.id === current) ? current : data[0]?.id ?? null))
  }

  useEffect(() => {
    Promise.allSettled([api.health(), api.providers(), api.projects.list()]).then(results => {
      if (results[0].status === 'fulfilled') setOnline(true)
      if (results[1].status === 'fulfilled') setProviders(results[1].value)
      if (results[2].status === 'fulfilled') {
        setProjects(results[2].value)
        setSelectedId(results[2].value[0]?.id ?? null)
      } else {
        notify('백엔드 서버에 연결할 수 없습니다.', 'error')
      }
    })
  }, [])

  const createProject = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try {
      const project = await api.projects.create(name.trim(), description.trim())
      await loadProjects(project.id)
      setCreateOpen(false)
      setName('')
      setDescription('')
      setTab('workspace')
      notify('프로젝트를 만들었습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '프로젝트 생성에 실패했습니다.', 'error')
    } finally {
      setBusy(false)
    }
  }

  const updateProject = (project: Project) => {
    setProjects(current => current.map(item => item.id === project.id ? project : item))
  }

  const refreshProject = async (projectId: string) => {
    try {
      updateProject(await api.projects.get(projectId))
    } catch (error) {
      notify(error instanceof Error ? error.message : '프로젝트 정보를 갱신하지 못했습니다.', 'error')
    }
  }

  const removeProject = async (projectId: string) => {
    await loadProjects()
    if (selectedId === projectId) setTab('workspace')
  }

  return (
    <div className={`app-shell ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <Sidebar
        projects={projects}
        selectedId={selectedId}
        online={online}
        collapsed={sidebarCollapsed}
        onSelect={setSelectedId}
        onCreate={() => setCreateOpen(true)}
        onToggle={() => setSidebarCollapsed(current => !current)}
      />
      <main className="main-area">
        {!selected ? (
          <section className="welcome">
            <div className="welcome-mark"><Sparkles size={30} /></div>
            <p className="eyebrow">LOCAL-FIRST AI WORKSPACE</p>
            <h1>프로젝트의 맥락을<br />한곳에서 이어가세요.</h1>
            <p>코드 디렉터리를 올리고, 파일·대화·규칙을 프로젝트 단위로 관리합니다.</p>
            <button className="primary-button" onClick={() => setCreateOpen(true)}>새 프로젝트 만들기</button>
          </section>
        ) : (
          <>
            <header className="project-header">
              <div>
                <div className="header-title-row"><h1>{selected.name}</h1><span className={`project-state ${selected.status.toLowerCase()}`}>{selected.status === 'READY' ? '동기화됨' : '파일 업로드 전'}</span></div>
                <p>{selected.description || '프로젝트 설명이 없습니다.'}</p>
              </div>
              <a className="secondary-button" href={api.files.archiveUrl(selected.id)}>ZIP 내보내기</a>
            </header>
            <nav className="tab-bar">
              {tabs.map(item => {
                const Icon = item.icon
                return <button key={item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}><Icon size={16} />{item.label}</button>
              })}
            </nav>
            <div className="view-container">
              {tab === 'workspace' && <WorkspaceView project={selected} onProjectRefresh={() => refreshProject(selected.id)} notify={notify} />}
              {tab === 'chat' && <ChatView project={selected} providers={providers} notify={notify} />}
              {tab === 'memory' && <MemoryView project={selected} notify={notify} />}
              {tab === 'runs' && <RunsView project={selected} notify={notify} />}
              {tab === 'settings' && <SettingsView project={selected} providers={providers} onUpdated={updateProject} onRemoved={removeProject} notify={notify} />}
            </div>
          </>
        )}
      </main>

      {createOpen && (
        <Modal title="새 프로젝트" onClose={() => setCreateOpen(false)}>
          <form className="form-stack" onSubmit={createProject}>
            <label>프로젝트 이름<input autoFocus maxLength={100} value={name} onChange={event => setName(event.target.value)} placeholder="예: student-api" /></label>
            <label>간단한 설명<textarea maxLength={500} value={description} onChange={event => setDescription(event.target.value)} placeholder="무엇을 만드는 프로젝트인가요?" /></label>
            <div className="modal-actions"><button type="button" className="ghost-button" onClick={() => setCreateOpen(false)}>취소</button><button className="primary-button" disabled={busy || !name.trim()}>{busy ? '생성 중…' : '프로젝트 생성'}</button></div>
          </form>
        </Modal>
      )}
      {toast && <div className={`toast ${toast.tone}`}>{toast.message}</div>}
    </div>
  )
}

import { FolderKanban, PanelLeftClose, PanelLeftOpen, Plus, Server, Sparkles } from 'lucide-react'
import type { Project } from '../types'

type Props = {
  projects: Project[]
  selectedId: string | null
  online: boolean
  collapsed: boolean
  onSelect: (id: string) => void
  onCreate: () => void
  onToggle: () => void
}

const statusLabel: Record<Project['status'], string> = {
  CREATED: '준비 중', UPLOADING: '업로드 중', INDEXING: '인덱싱', ANALYZING: '분석 중',
  READY: '준비됨', FAILED: '확인 필요',
}

export default function Sidebar({ projects, selectedId, online, collapsed, onSelect, onCreate, onToggle }: Props) {
  return (
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
      <div className="brand">
        <span className="brand-mark"><Sparkles size={18} /></span>
        <div className="brand-copy"><strong>LocalFlow</strong><small>AI workspace</small></div>
      </div>
      <button
        className="sidebar-toggle"
        onClick={onToggle}
        title={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
        aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
        aria-expanded={!collapsed}
      >
        {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
      </button>

      <div className="sidebar-section-title">
        <span>프로젝트</span>
        <button className="icon-button" onClick={onCreate} title="새 프로젝트"><Plus size={17} /></button>
      </div>

      <nav className="project-list" aria-label="프로젝트 목록">
        {projects.length === 0 && (
          <button className="empty-project" onClick={onCreate}>
            <FolderKanban size={21} />
            <span>첫 프로젝트 만들기</span>
          </button>
        )}
        {projects.map(project => (
          <button
            key={project.id}
            className={`project-item ${selectedId === project.id ? 'active' : ''}`}
            onClick={() => onSelect(project.id)}
            title={collapsed ? project.name : undefined}
          >
            <span className="project-icon"><FolderKanban size={17} /></span>
            <span className="project-copy">
              <strong>{project.name}</strong>
              <small>{project.rootDirectoryName || statusLabel[project.status]}</small>
            </span>
            <span className={`status-dot ${project.status.toLowerCase()}`} />
          </button>
        ))}
      </nav>

      <div className="sidebar-footer" title={collapsed ? `백엔드: ${online ? '연결됨' : '연결 안 됨'}` : undefined}>
        <Server size={15} />
        <span>백엔드</span>
        <strong className={online ? 'online' : 'offline'}>{online ? '연결됨' : '연결 안 됨'}</strong>
      </div>
    </aside>
  )
}

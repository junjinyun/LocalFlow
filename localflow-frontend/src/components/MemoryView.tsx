import { useEffect, useState } from 'react'
import { Brain, CheckCircle2, Circle, Edit3, LoaderCircle, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { api } from '../api'
import type { MemoryType, Project, ProjectMemory } from '../types'
import Modal from './Modal'

const labels: Record<MemoryType, string> = {
  PROJECT_RULE: '프로젝트 규칙', IMPLEMENTATION_DECISION: '구현 결정', OPEN_TASK: '남은 작업',
  ERROR_CONTEXT: '오류 맥락', NOTE: '참고 메모',
}

export default function MemoryView({ project, notify }: { project: Project; notify: (message: string, tone?: 'success' | 'error') => void }) {
  const [memories, setMemories] = useState<ProjectMemory[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ProjectMemory | null>(null)
  const [type, setType] = useState<MemoryType>('PROJECT_RULE')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [activeOnly, setActiveOnly] = useState(false)

  const load = async () => {
    setLoading(true)
    try { setMemories(await api.memories.list(project.id, activeOnly)) }
    catch (error) { notify(error instanceof Error ? error.message : '프로젝트 기억을 불러오지 못했습니다.', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [project.id, activeOnly])

  const openCreate = () => {
    setEditing(null); setType('PROJECT_RULE'); setTitle(''); setContent(''); setModalOpen(true)
  }
  const openEdit = (memory: ProjectMemory) => {
    setEditing(memory); setType(memory.type); setTitle(memory.title); setContent(memory.content); setModalOpen(true)
  }

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    try {
      if (editing) await api.memories.update(project.id, { ...editing, type, title: title.trim(), content: content.trim() })
      else await api.memories.create(project.id, type, title.trim(), content.trim())
      await load()
      setModalOpen(false)
      notify(editing ? '기억을 수정했습니다.' : '새 기억을 등록했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '기억을 저장하지 못했습니다.', 'error')
    }
  }

  const toggle = async (memory: ProjectMemory) => {
    try {
      const updated = await api.memories.update(project.id, { ...memory, active: !memory.active })
      setMemories(current => current
        .map(item => item.id === updated.id ? updated : item)
        .filter(item => !activeOnly || item.active))
    } catch (error) { notify(error instanceof Error ? error.message : '상태를 변경하지 못했습니다.', 'error') }
  }

  const remove = async (memory: ProjectMemory) => {
    if (!window.confirm(`“${memory.title}” 기억을 삭제할까요?`)) return
    try {
      await api.memories.remove(project.id, memory.id)
      setMemories(current => current.filter(item => item.id !== memory.id))
      notify('기억을 삭제했습니다.')
    } catch (error) { notify(error instanceof Error ? error.message : '기억을 삭제하지 못했습니다.', 'error') }
  }

  return (
    <section className="content-panel panel">
      <div className="section-heading">
        <div><p className="eyebrow">PROJECT MEMORY</p><h2>프로젝트 기억</h2><p>활성 항목은 향후 AI 요청의 전처리 맥락으로 사용할 수 있습니다.</p></div>
        <div className="inline-actions"><label className="check-row"><input type="checkbox" checked={activeOnly} onChange={event => setActiveOnly(event.target.checked)} />활성 항목만</label><button className="icon-button" onClick={load} title="새로고침"><RefreshCw size={16} /></button><button className="primary-button" onClick={openCreate}><Plus size={16} />기억 추가</button></div>
      </div>
      {loading ? <div className="center-state"><LoaderCircle className="spin" /></div> : memories.length === 0 ? (
        <div className="empty-state roomy"><Brain size={31} /><h3>저장된 프로젝트 기억이 없습니다.</h3><p>반드시 지켜야 할 규칙이나 구현 결정을 남겨보세요.</p><button className="secondary-button" onClick={openCreate}>첫 기억 추가</button></div>
      ) : (
        <div className="memory-grid">
          {memories.map(memory => (
            <article className={`memory-card ${memory.active ? '' : 'inactive'}`} key={memory.id}>
              <header><span className={`memory-type ${memory.type.toLowerCase()}`}>{labels[memory.type]}</span><button className="active-toggle" onClick={() => toggle(memory)}>{memory.active ? <CheckCircle2 size={16} /> : <Circle size={16} />}{memory.active ? '활성' : '비활성'}</button></header>
              <h3>{memory.title}</h3><p>{memory.content}</p>
              <footer><time>{new Date(memory.updatedAt).toLocaleDateString('ko-KR')}</time><div className="inline-actions"><button className="icon-button subtle" onClick={() => openEdit(memory)}><Edit3 size={15} /></button><button className="icon-button subtle danger" onClick={() => remove(memory)}><Trash2 size={15} /></button></div></footer>
            </article>
          ))}
        </div>
      )}

      {modalOpen && (
        <Modal title={editing ? '기억 수정' : '새 프로젝트 기억'} onClose={() => setModalOpen(false)}>
          <form className="form-stack" onSubmit={save}>
            <label>유형<select value={type} onChange={event => setType(event.target.value as MemoryType)}>{Object.entries(labels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
            <label>제목<input autoFocus maxLength={150} value={title} onChange={event => setTitle(event.target.value)} placeholder="예: 공통 오류 응답 사용" /></label>
            <label>내용<textarea className="tall" maxLength={10000} value={content} onChange={event => setContent(event.target.value)} placeholder="앞으로 반영할 내용을 구체적으로 적어주세요." /></label>
            <div className="modal-actions"><button type="button" className="ghost-button" onClick={() => setModalOpen(false)}>취소</button><button className="primary-button" disabled={!title.trim() || !content.trim()}>저장</button></div>
          </form>
        </Modal>
      )}
    </section>
  )
}

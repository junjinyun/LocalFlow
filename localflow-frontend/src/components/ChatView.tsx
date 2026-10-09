import { useEffect, useRef, useState } from 'react'
import { Bot, CornerDownLeft, Info, LoaderCircle, MessageSquare, Send, Trash2, UserRound } from 'lucide-react'
import { api } from '../api'
import type { AgentRunProgress, ChatMessage, ExecutionMode, PrivacyMode, Project, Provider, ProviderType } from '../types'
import AgentProgressTimeline from './AgentProgressTimeline'
import MarkdownContent from './MarkdownContent'

type Props = {
  project: Project
  providers: Provider[]
  notify: (message: string, tone?: 'success' | 'error') => void
}

const typeLabels: Record<ChatMessage['type'], string> = {
  PROMPT: '프롬프트', HANDOFF: '인수인계', NOTE: '메모', SYSTEM: '시스템',
}

export default function ChatView({ project, providers, notify }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [content, setContent] = useState('')
  const [type, setType] = useState<ChatMessage['type']>('PROMPT')
  const [executionMode, setExecutionMode] = useState<ExecutionMode>('BALANCED')
  const [privacyMode, setPrivacyMode] = useState<PrivacyMode>('LOCAL_ONLY')
  const [provider, setProvider] = useState<ProviderType | ''>('')
  const [model, setModel] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [activeProgress, setActiveProgress] = useState<AgentRunProgress[]>([])
  const [activeRunStatus, setActiveRunStatus] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const progressTimerRef = useRef<number | null>(null)
  const generationProviders = providers.filter(item => item.role === 'GENERATION')
  const selectedProvider = generationProviders.find(item => item.type === provider)
  const selectableModels = selectedProvider?.models?.length
    ? selectedProvider.models
    : selectedProvider?.model ? [selectedProvider.model] : []
  const modelSelectable = provider === 'OPENAI' || provider === 'VERTEX_AI' || provider === 'OLLAMA'
  const privacyBlocked = privacyMode === 'LOCAL_ONLY' && provider !== '' && provider !== 'OLLAMA'
  const modelValid = !modelSelectable || Boolean(model && selectableModels.includes(model))
  const canExecute = Boolean(selectedProvider?.available) && !privacyBlocked && modelValid

  const providerUnavailableLabel = (item: Provider) => {
    if (item.available) return ''
    if (item.type === 'OLLAMA' && item.configured && !item.reachable) return ' · 서버 연결 실패'
    if (item.type === 'OLLAMA' && item.reachable && !item.modelInstalled) return ' · 모델 없음'
    return ' · 환경 설정 필요'
  }

  const load = async () => {
    setLoading(true)
    try {
      const [history, settings] = await Promise.all([api.chat.list(project.id), api.projects.settings(project.id)])
      setMessages(history)
      setExecutionMode(settings.executionMode)
      setPrivacyMode(settings.privacyMode)
    } catch (error) {
      notify(error instanceof Error ? error.message : '대화 기록을 불러오지 못했습니다.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const stopProgressPolling = () => {
    if (progressTimerRef.current != null) window.clearInterval(progressTimerRef.current)
    progressTimerRef.current = null
  }

  const refreshProgress = async (runId: string) => {
    try {
      const events = await api.runs.progress(project.id, runId)
      setActiveProgress(current => events.length > 0 ? events : current)
    }
    catch { /* 실행 요청이 진행 중일 때의 일시적인 조회 실패는 다음 폴링에서 복구한다. */ }
  }

  useEffect(() => {
    setActiveProgress([])
    setActiveRunStatus(null)
    load()
    return stopProgressPolling
  }, [project.id])
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages.length, activeProgress.length])
  useEffect(() => {
    if (!provider && generationProviders.length) {
      setProvider((generationProviders.find(item => item.type === 'OLLAMA' && item.available)
        ?? generationProviders.find(item => item.available)
        ?? generationProviders[0]).type)
    }
  }, [providers])
  useEffect(() => {
    if (!modelSelectable || !selectedProvider) {
      setModel('')
      return
    }
    setModel(selectedProvider.model && selectableModels.includes(selectedProvider.model)
      ? selectedProvider.model
      : selectableModels[0] ?? '')
  }, [provider, selectedProvider?.model, selectedProvider?.models, modelSelectable])

  const submit = async (createRun: boolean) => {
    if (!content.trim()) return
    setSending(true)
    if (createRun) {
      setActiveRunStatus('PREPARING')
      setActiveProgress([{
        id: -Date.now(),
        stage: 'PREPARING',
        message: '요청을 등록하고 AI 실행을 준비하는 중입니다.',
        createdAt: new Date().toISOString(),
      }])
    }
    try {
      const prompt = content.trim()
      const message = await api.chat.create(project.id, type, prompt)
      setMessages(current => [...current, message])
      if (createRun) {
        const draft = await api.runs.create(
          project.id,
          prompt,
          executionMode,
          provider || null,
          modelSelectable ? model : null,
        )
        await refreshProgress(draft.runId)
        progressTimerRef.current = window.setInterval(() => refreshProgress(draft.runId), 700)
        const queued = await api.runs.execute(project.id, draft.runId)
        setActiveRunStatus(queued.status)
        const run = await api.runs.waitForCompletion(project.id, draft.runId, update => {
          setActiveRunStatus(update.status)
        })
        stopProgressPolling()
        await refreshProgress(draft.runId)
        setActiveRunStatus(run.status)
        if (run.status === 'COMPLETED') {
          await load()
          setActiveProgress([])
          setActiveRunStatus(null)
          notify('AI 작업을 완료했습니다.')
        }
        else if (run.status === 'WAITING_APPROVAL') notify('계획을 만들었습니다. 실행 기록에서 승인해 주세요.')
        else if (run.status === 'FAILED') notify(run.errorMessage || 'AI 실행에 실패했습니다.', 'error')
        else notify(`AI 실행 상태: ${run.status}`)
      } else {
        notify('대화 기록을 저장했습니다.')
      }
      setContent('')
    } catch (error) {
      setActiveRunStatus('FAILED')
      notify(error instanceof Error ? error.message : '요청을 저장하지 못했습니다.', 'error')
    } finally {
      stopProgressPolling()
      setSending(false)
    }
  }

  const remove = async (messageId: string) => {
    try {
      await api.chat.remove(project.id, messageId)
      setMessages(current => current.filter(message => message.id !== messageId))
    } catch (error) {
      notify(error instanceof Error ? error.message : '기록을 삭제하지 못했습니다.', 'error')
    }
  }

  return (
    <section className="chat-layout">
      <div className="chat-main panel">
        <div className="section-heading">
          <div><p className="eyebrow">PROJECT CONVERSATION</p><h2>프로젝트 대화</h2><p>중요한 규칙은 기억 탭에 별도로 남길 수 있습니다.</p></div>
          <span className="count-badge">{messages.length}</span>
        </div>
        <div className="message-list">
          {loading && <div className="center-state compact"><LoaderCircle className="spin" /></div>}
          {!loading && messages.length === 0 && <div className="empty-state"><MessageSquare size={28} /><h3>아직 기록이 없습니다.</h3><p>첫 작업 요청이나 다른 AI의 인수인계 내용을 남겨보세요.</p></div>}
          {messages.map(message => (
            <article className="message-card" key={message.id}>
              <span className="message-avatar">{message.type === 'SYSTEM' ? <Bot size={16} /> : <UserRound size={16} />}</span>
              <div><header><strong>{typeLabels[message.type]}</strong><time>{new Date(message.createdAt).toLocaleString('ko-KR')}</time></header><MarkdownContent content={message.content} /></div>
              <button className="icon-button subtle danger" onClick={() => remove(message.id)}><Trash2 size={14} /></button>
            </article>
          ))}
          {activeProgress.length > 0 && <article className="message-card agent-activity-card">
            <span className="message-avatar"><Bot size={16} /></span>
            <div>
              <header><strong>AI 에이전트</strong><span className={`agent-activity-status ${activeRunStatus?.toLowerCase() ?? ''}`}>{sending ? '작업 중' : activeRunStatus === 'WAITING_APPROVAL' ? '승인 대기' : activeRunStatus === 'FAILED' ? '실패' : '진행 기록'}</span></header>
              <AgentProgressTimeline events={activeProgress} active={sending} />
            </div>
          </article>}
          <div ref={bottomRef} />
        </div>
        <div className="composer">
          <textarea value={content} onChange={event => setContent(event.target.value)} onKeyDown={event => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) { event.preventDefault(); submit(false) }
          }} placeholder="작업 요청, 인수인계 또는 프로젝트 메모를 입력하세요…" />
          <div className="composer-footer">
            <div className="inline-fields">
              <select value={type} onChange={event => setType(event.target.value as ChatMessage['type'])}><option value="PROMPT">프롬프트</option><option value="HANDOFF">인수인계</option><option value="NOTE">메모</option></select>
              <span>Ctrl + Enter로 기록</span>
            </div>
            <div className="inline-actions"><button className="secondary-button" disabled={sending || !content.trim()} onClick={() => submit(false)}><Send size={15} />기록</button><button className="primary-button" disabled={sending || !content.trim() || !canExecute} onClick={() => submit(true)} title={!selectedProvider?.available ? selectedProvider?.statusMessage || '사용 가능한 AI 제공자가 없습니다.' : privacyBlocked ? '로컬 전용에서는 Ollama만 사용할 수 있습니다.' : !modelValid ? '사용할 모델을 선택해 주세요.' : 'AI 작업 실행'}><CornerDownLeft size={15} />AI 실행</button></div>
          </div>
        </div>
      </div>

      <aside className="chat-side panel">
        <div className="side-note"><Info size={17} /><div><strong>AI 실행이 연결되어 있습니다.</strong><p>선택한 제공자로 계획을 만들고, 프로젝트 권한에 따라 승인 후 업로드 사본에 적용합니다.</p></div></div>
        {privacyBlocked && <div className="side-note warning"><Info size={17} /><div><strong>로컬 전용 설정</strong><p>외부 AI를 실행하려면 설정 탭에서 개인정보 모드를 외부 AI 전송 허용으로 변경해야 합니다.</p></div></div>}
        {selectedProvider && !selectedProvider.available && <div className="side-note warning"><Info size={17} /><div><strong>{selectedProvider.displayName}을 사용할 수 없습니다.</strong><p>{selectedProvider.statusMessage}</p></div></div>}
        <div className="form-stack compact-form">
          <label>실행 모드<select value={executionMode} onChange={event => setExecutionMode(event.target.value as ExecutionMode)}><option value="CONFIRM_EVERY_STEP">매 단계 확인</option><option value="BALANCED">균형 모드</option><option value="AUTONOMOUS">자율 실행</option></select></label>
          <label>생성 AI<select value={provider} onChange={event => setProvider(event.target.value as ProviderType)}>{generationProviders.map(item => <option key={item.type} value={item.type} disabled={!item.available}>{item.displayName}{providerUnavailableLabel(item)}</option>)}</select></label>
          {modelSelectable && <label>사용 모델<select value={model} onChange={event => setModel(event.target.value)} disabled={!selectableModels.length}>{selectableModels.map(item => <option key={item} value={item}>{item}{item === selectedProvider?.model ? ' · 기본' : ''}</option>)}</select></label>}
        </div>
        <div className="context-guide"><h3>기록 유형</h3><dl><dt>프롬프트</dt><dd>수행하려는 작업</dd><dt>인수인계</dt><dd>이전 AI의 진행 내용</dd><dt>메모</dt><dd>일회성 참고 사항</dd></dl></div>
      </aside>
    </section>
  )
}

import { useEffect, useState } from 'react'
import { ArrowLeft, Ban, Bot, CheckCircle2, ChevronRight, Clock3, FileClock, LoaderCircle, Play, RefreshCw } from 'lucide-react'
import { api } from '../api'
import type { AgentDecision, AgentFileChangeSnapshot, AgentInputSnapshot, AgentPlan, AgentRun, AgentRunProgress, AgentRunSummary, Project, TaskDecomposition } from '../types'
import AgentProgressTimeline from './AgentProgressTimeline'
import FileChangeDiff from './FileChangeDiff'
import MarkdownContent from './MarkdownContent'

export default function RunsView({ project, notify }: { project: Project; notify: (message: string, tone?: 'success' | 'error') => void }) {
  const [runs, setRuns] = useState<AgentRunSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [progress, setProgress] = useState<AgentRunProgress[]>([])

  const load = async () => {
    setLoading(true)
    try { setRuns(await api.runs.list(project.id)) }
    catch (error) { notify(error instanceof Error ? error.message : '실행 기록을 불러오지 못했습니다.', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => {
    setSelectedRun(null)
    setProgress([])
    load()
  }, [project.id])

  const storeRun = (updated: AgentRun) => {
    setRuns(current => current.map(run => run.runId === updated.runId ? summaryFromRun(updated) : run))
    setSelectedRun(current => current?.runId === updated.runId ? updated : current)
  }

  const cancel = async (runId: string) => {
    try {
      const updated = await api.runs.cancel(project.id, runId)
      storeRun(updated)
      notify('실행 초안을 취소했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '실행 취소에 실패했습니다.', 'error')
    }
  }

  const execute = async (runId: string, approved: boolean) => {
    setWorkingId(runId)
    const refreshProgress = async () => {
      try { setProgress(await api.runs.progress(project.id, runId)) }
      catch { /* 실행 중 다음 조회에서 복구한다. */ }
    }
    await refreshProgress()
    const timer = window.setInterval(refreshProgress, 700)
    try {
      const updated = await api.runs.execute(project.id, runId, approved)
      await refreshProgress()
      storeRun(updated)
      if (updated.status === 'COMPLETED') notify('AI 작업을 완료했습니다.')
      else if (updated.status === 'WAITING_APPROVAL') notify('파일 작업 전 승인이 필요합니다.')
      else if (updated.status === 'FAILED') notify(updated.errorMessage || 'AI 실행에 실패했습니다.', 'error')
    } catch (error) {
      notify(error instanceof Error ? error.message : 'AI 실행에 실패했습니다.', 'error')
    } finally {
      window.clearInterval(timer)
      setWorkingId(null)
    }
  }

  const refreshOne = async (runId: string) => {
    setWorkingId(runId)
    try {
      const updated = await api.runs.get(project.id, runId)
      storeRun(updated)
      notify('실행 상태를 갱신했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : '실행 상태를 불러오지 못했습니다.', 'error')
    } finally { setWorkingId(null) }
  }

  const planOf = (run: AgentRun): AgentPlan | null => {
    if (!run.planJson) return null
    try { return JSON.parse(run.planJson) as AgentPlan } catch { return null }
  }

  const decisionOf = (run: AgentRun): AgentDecision | null => {
    if (!run.decisionJson) return null
    try { return JSON.parse(run.decisionJson) as AgentDecision } catch { return null }
  }

  const decompositionOf = (run: AgentRun): TaskDecomposition | null => {
    if (!run.decompositionJson) return null
    try { return JSON.parse(run.decompositionJson) as TaskDecomposition } catch { return null }
  }

  const changesOf = (run: AgentRun): AgentFileChangeSnapshot[] | null => {
    if (!run.changesJson) return null
    try { return JSON.parse(run.changesJson) as AgentFileChangeSnapshot[] } catch { return null }
  }

  const inputSnapshotOf = (run: AgentRun): AgentInputSnapshot | null => {
    if (!run.inputSnapshotJson) return null
    try { return JSON.parse(run.inputSnapshotJson) as AgentInputSnapshot } catch { return null }
  }

  const summaryFromRun = (run: AgentRun): AgentRunSummary => {
    const plan = planOf(run)
    return {
      runId: run.runId,
      projectId: run.projectId,
      prompt: run.prompt,
      executionMode: run.executionMode,
      preferredGenerationProvider: run.preferredGenerationProvider,
      preferredModel: run.preferredModel,
      actualGenerationProvider: run.actualGenerationProvider,
      status: run.status,
      notice: run.notice,
      resultSummary: run.errorMessage || plan?.summary || run.notice,
      fileChanges: plan?.operations.map(operation => ({
        action: operation.action,
        path: operation.path,
        destinationPath: operation.destinationPath,
      })) ?? [],
      errorMessage: run.errorMessage,
      model: run.model,
      createdAt: run.createdAt,
      updatedAt: run.updatedAt,
      completedAt: run.completedAt,
    }
  }

  const openDetail = async (run: AgentRunSummary) => {
    setDetailLoading(true)
    try {
      const [detail, events] = await Promise.all([
        api.runs.get(project.id, run.runId),
        api.runs.progress(project.id, run.runId),
      ])
      storeRun(detail)
      setSelectedRun(detail)
      setProgress(events)
    } catch (error) {
      notify(error instanceof Error ? error.message : '실행 상세 정보를 불러오지 못했습니다.', 'error')
    } finally {
      setDetailLoading(false)
    }
  }

  if (selectedRun) {
    const plan = planOf(selectedRun)
    const decision = decisionOf(selectedRun)
    const decomposition = decompositionOf(selectedRun)
    const recordedChanges = changesOf(selectedRun)
    const inputSnapshot = inputSnapshotOf(selectedRun)
    const changes = recordedChanges ?? (plan?.operations.map(operation => ({
      action: operation.action,
      path: operation.path,
      destinationPath: operation.destinationPath,
      beforeContent: null,
      afterContent: operation.content,
    })) ?? [])
    const fullResponse = selectedRun.errorMessage || plan?.response || selectedRun.resultMessage || selectedRun.notice

    return (
      <section className="content-panel panel run-detail-page">
        <div className="section-heading run-detail-heading">
          <div className="run-detail-title">
            <button className="icon-button" onClick={() => setSelectedRun(null)} title="실행 기록 목록으로"><ArrowLeft size={17} /></button>
            <div><p className="eyebrow">RUN DETAIL</p><h2>실행 상세</h2><p>AI 응답, 파일 변경 계획과 실행 정보를 확인합니다.</p></div>
          </div>
          <button className="secondary-button" disabled={workingId === selectedRun.runId} onClick={() => refreshOne(selectedRun.runId)}><RefreshCw className={workingId === selectedRun.runId ? 'spin' : ''} size={15} />새로고침</button>
        </div>

        <div className="run-detail-content">
          <div className="run-detail-summary">
            <div>
              <span className={`run-status ${selectedRun.status.toLowerCase()}`}>{selectedRun.status}</span>
              <time><Clock3 size={13} />{new Date(selectedRun.createdAt).toLocaleString('ko-KR')}</time>
            </div>
            <h3>{selectedRun.prompt}</h3>
            <p className={selectedRun.errorMessage ? 'run-error' : ''}>{plan?.summary || selectedRun.resultMessage || selectedRun.notice}</p>
          </div>

          {progress.length > 0 && <section className="run-detail-section">
            <h3>에이전트 진행 과정 <span>{progress.length}</span></h3>
            <AgentProgressTimeline events={progress} active={workingId === selectedRun.runId} />
          </section>}

          <section className="run-detail-section">
            <h3>작업 분해 {decomposition && <span>{decomposition.tasks.length}</span>}</h3>
            {!decomposition ? <p className="run-detail-empty">저장된 작업 분해 결과가 없습니다.</p> : <>
              <p className="run-snapshot-notice">{decomposition.source === 'AI' ? '선택한 생성 AI가 의미를 분석해 분해했습니다.' : '생성 AI 분해 실패로 로컬 규칙을 사용했습니다.'}</p>
              <ol className="decomposition-list">{decomposition.tasks.map(task => <li key={task.id}>
                <strong>{task.instruction}</strong>
                {task.dependsOn.length > 0 && <small>선행 작업: {task.dependsOn.join(', ')}</small>}
                {task.suggestedTags.length > 0 && <small>제안 태그: {task.suggestedTags.join(' · ')}</small>}
                {task.acceptanceCriteria.length > 0 && <ul>{task.acceptanceCriteria.map(criteria => <li key={criteria}>{criteria}</li>)}</ul>}
              </li>)}</ol>
            </>}
          </section>

          <section className="run-detail-section">
            <h3>작업 결과</h3>
            {detailLoading ? <div className="center-state compact"><LoaderCircle className="spin" /></div> : <MarkdownContent content={fullResponse || '반환된 상세 내용이 없습니다.'} />}
          </section>

          <section className="run-detail-section">
            <h3>파일 변경 <span>{changes.length}</span></h3>
            {recordedChanges == null && changes.length > 0 && <p className="run-snapshot-notice">이전 형식의 실행 기록입니다. 변경 계획은 확인할 수 있지만 변경 전 본문은 저장되어 있지 않을 수 있습니다.</p>}
            {changes.length === 0 ? <p className="run-detail-empty">변경된 파일이 없습니다.</p> : (
              <ul className="run-operation-list">
                {changes.map((change, index) => (
                  <li key={`${change.path}-${index}`}>
                    <details>
                      <summary>
                        <b className={change.action.toLowerCase()}>{change.action}</b>
                        <code>{change.path}</code>
                        {change.destinationPath && <span>→ {change.destinationPath}</span>}
                        <small>변경 내용 보기</small>
                        <ChevronRight size={16} />
                      </summary>
                      <FileChangeDiff change={change} />
                    </details>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="run-detail-section">
            <h3>실행 정보</h3>
            <dl className="run-metadata">
              <dt>실행 모드</dt><dd>{selectedRun.executionMode}</dd>
              <dt>AI 제공자</dt><dd>{selectedRun.actualGenerationProvider || selectedRun.preferredGenerationProvider || '지정되지 않음'}</dd>
              <dt>모델</dt><dd>{selectedRun.model || decision?.model || '확인되지 않음'}</dd>
              <dt>토큰</dt><dd>입력 {selectedRun.inputTokens ?? 0} · 출력 {selectedRun.outputTokens ?? 0}</dd>
              <dt>실행 ID</dt><dd><code>{selectedRun.runId}</code></dd>
              {decision && <><dt>AI 판단</dt><dd>{decision.actionType} · 위험도 {decision.riskLevel} · 대상 {decision.targetPath || '자동 선택'}</dd></>}
              {decision?.taskScopes && decision.taskScopes.length > 0 && <><dt>작업 분해</dt><dd><ol className="decision-task-list">{decision.taskScopes.map((scope, index) => <li key={`${scope.instruction}-${index}`}><strong>{scope.instruction}</strong>{scope.tags.length > 0 && <small>{scope.tags.join(' · ')}</small>}</li>)}</ol></dd></>}
              {decision?.selectedTags && decision.selectedTags.length > 0 && <><dt>선택 태그</dt><dd>{decision.selectedTags.join(' · ')}</dd></>}
            </dl>
            {!inputSnapshot ? <p className="run-input-empty">이 실행에는 저장된 AI 입력 스냅샷이 없습니다.</p> : <div className="run-input-snapshot">
              <details>
                <summary>AI에 본문을 전달한 파일 <span>{inputSnapshot.contextFiles.length}</span><ChevronRight size={15} /></summary>
                {inputSnapshot.contextFiles.length === 0
                  ? <p>파일 본문을 전달하지 않았습니다.</p>
                  : <ul>{inputSnapshot.contextFiles.map(path => <li key={path}><code>{path}</code></li>)}</ul>}
              </details>
              <details>
                <summary>시스템 전처리 프롬프트 <ChevronRight size={15} /></summary>
                <pre>{inputSnapshot.systemPrompt}</pre>
              </details>
              <details>
                <summary>사용자 전처리 프롬프트 <ChevronRight size={15} /></summary>
                <pre>{inputSnapshot.userPrompt}</pre>
              </details>
            </div>}
          </section>

          <div className="run-detail-actions">
            {selectedRun.status === 'DRAFT' && <button className="primary-button" disabled={workingId === selectedRun.runId} onClick={() => execute(selectedRun.runId, false)}>{workingId === selectedRun.runId ? <LoaderCircle className="spin" size={15} /> : <Play size={15} />}실행</button>}
            {selectedRun.status === 'WAITING_APPROVAL' && <button className="primary-button" disabled={workingId === selectedRun.runId} onClick={() => execute(selectedRun.runId, true)}>{workingId === selectedRun.runId ? <LoaderCircle className="spin" size={15} /> : <CheckCircle2 size={15} />}{selectedRun.planJson ? '승인 및 적용' : '승인하고 계속'}</button>}
            {['DRAFT', 'PENDING', 'WAITING_APPROVAL'].includes(selectedRun.status) && <button className="secondary-button danger-text" disabled={workingId === selectedRun.runId} onClick={() => cancel(selectedRun.runId)}><Ban size={15} />취소</button>}
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="content-panel panel">
      <div className="section-heading">
        <div><p className="eyebrow">AGENT RUNS</p><h2>실행 기록</h2><p>프롬프트별 AI 계획, 승인 대기, 파일 적용 결과를 확인합니다.</p></div>
        <button className="secondary-button" onClick={load}><RefreshCw size={15} />새로고침</button>
      </div>
      {loading ? <div className="center-state"><LoaderCircle className="spin" /></div> : runs.length === 0 ? (
        <div className="empty-state roomy"><FileClock size={30} /><h3>실행 초안이 없습니다.</h3><p>대화 탭에서 프롬프트를 입력하고 실행 초안을 만들어보세요.</p></div>
      ) : (
        <div className="run-list">
          {runs.map(run => {
            const operations = run.fileChanges
            const summary = run.resultSummary || (
              run.status === 'COMPLETED' ? 'AI 작업이 완료되었습니다. 상세 화면에서 결과를 확인할 수 있습니다.'
                : run.status === 'WAITING_APPROVAL' ? '파일 변경 계획이 준비되어 승인을 기다리고 있습니다.'
                  : run.status === 'FAILED' ? '실행에 실패했습니다. 상세 화면에서 오류 내용을 확인해 주세요.'
                    : 'AI 작업이 준비되었거나 진행 중입니다.'
            )
            const actionCounts = operations.reduce<Record<string, number>>((counts, operation) => {
              counts[operation.action] = (counts[operation.action] ?? 0) + 1
              return counts
            }, {})
            return <article className="run-card" key={run.runId}>
              <div
                className="run-card-summary"
                role="button"
                tabIndex={0}
                onClick={() => openDetail(run)}
                onKeyDown={event => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    openDetail(run)
                  }
                }}
                title="실행 상세 보기"
              >
                <span className="run-icon"><Bot size={20} /></span>
                <div className="run-copy">
                  <header><span className={`run-status ${run.status.toLowerCase()}`}>{run.status}</span><time><Clock3 size={13} />{new Date(run.createdAt).toLocaleString('ko-KR')}</time></header>
                  <h3>{run.prompt}</h3>
                  <p className={run.errorMessage ? 'run-error' : ''}>{summary}</p>
                  <span className="run-change-overview">
                    <strong>{operations.length > 0 ? `${operations.length}개 파일 변경` : '파일 변경 없음'}</strong>
                    {operations.length > 0 && <span>{Object.entries(actionCounts).map(([action, count]) => `${action} ${count}`).join(' · ')}</span>}
                  </span>
                  {operations.length > 0 && <span className="run-file-preview">{operations.slice(0, 3).map(operation => <code key={`${operation.action}-${operation.path}`}>{operation.path}</code>)}{operations.length > 3 && <small>외 {operations.length - 3}개</small>}</span>}
                </div>
                <ChevronRight className="run-open-icon" size={20} />
              </div>
              <div className="run-actions">
                <button className="icon-button" disabled={workingId === run.runId} onClick={() => refreshOne(run.runId)} title="실행 단건 조회"><RefreshCw className={workingId === run.runId ? 'spin' : ''} size={15} /></button>
                {run.status === 'DRAFT' && <button className="primary-button" disabled={workingId === run.runId} onClick={() => execute(run.runId, false)}>{workingId === run.runId ? <LoaderCircle className="spin" size={15} /> : <Play size={15} />}실행</button>}
                {run.status === 'WAITING_APPROVAL' && <button className="primary-button" disabled={workingId === run.runId} onClick={() => execute(run.runId, true)}>{workingId === run.runId ? <LoaderCircle className="spin" size={15} /> : <CheckCircle2 size={15} />}{operations.length > 0 ? '승인 및 적용' : '승인하고 계속'}</button>}
                {['DRAFT', 'PENDING', 'WAITING_APPROVAL'].includes(run.status) && <button className="secondary-button danger-text" disabled={workingId === run.runId} onClick={() => cancel(run.runId)}><Ban size={15} />취소</button>}
              </div>
            </article>
          })}
        </div>
      )}
    </section>
  )
}

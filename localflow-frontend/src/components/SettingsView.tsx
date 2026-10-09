import { useEffect, useState } from 'react'
import { AlertTriangle, Check, Cloud, HardDrive, LoaderCircle, RefreshCw, Save, ShieldCheck, Trash2 } from 'lucide-react'
import { api } from '../api'
import type { ExecutionMode, PermissionPolicy, PrivacyMode, Project, ProjectSettings, Provider } from '../types'

type Props = {
  project: Project
  providers: Provider[]
  onUpdated: (project: Project) => void
  onRemoved: (projectId: string) => Promise<void> | void
  notify: (message: string, tone?: 'success' | 'error') => void
}

const policyLabels: Record<PermissionPolicy, string> = { ALLOW: '항상 허용', CONFIRM: '매번 확인', DENY: '허용 안 함' }

export default function SettingsView({ project, providers, onUpdated, onRemoved, notify }: Props) {
  const [settings, setSettings] = useState<ProjectSettings | null>(null)
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? '')
  const [saving, setSaving] = useState(false)
  const [providerState, setProviderState] = useState(providers)
  const [refreshingProviders, setRefreshingProviders] = useState(false)

  useEffect(() => {
    setName(project.name); setDescription(project.description ?? ''); setSettings(null)
    api.projects.settings(project.id).then(setSettings).catch(error => notify(error instanceof Error ? error.message : '설정을 불러오지 못했습니다.', 'error'))
  }, [project.id])

  useEffect(() => { setProviderState(providers) }, [providers])

  const refreshProviders = async () => {
    setRefreshingProviders(true)
    try {
      setProviderState(await api.providers())
      notify('AI 제공자 상태를 갱신했습니다.')
    } catch (error) {
      notify(error instanceof Error ? error.message : 'AI 제공자 상태를 불러오지 못했습니다.', 'error')
    } finally { setRefreshingProviders(false) }
  }

  const updatePolicy = (key: keyof ProjectSettings, value: string) => {
    if (settings) setSettings({ ...settings, [key]: value })
  }

  const save = async () => {
    if (!settings) return
    setSaving(true)
    try {
      const [updatedProject, updatedSettings] = await Promise.all([
        api.projects.update(project.id, name.trim(), description.trim()),
        api.projects.updateSettings(project.id, {
          executionMode: settings.executionMode, privacyMode: settings.privacyMode,
          readPolicy: settings.readPolicy, createPolicy: settings.createPolicy,
          editPolicy: settings.editPolicy, movePolicy: settings.movePolicy,
          deletePolicy: settings.deletePolicy, executePolicy: settings.executePolicy,
        }),
      ])
      onUpdated(updatedProject); setSettings(updatedSettings); notify('프로젝트 설정을 저장했습니다.')
    } catch (error) { notify(error instanceof Error ? error.message : '설정 저장에 실패했습니다.', 'error') }
    finally { setSaving(false) }
  }

  const remove = async () => {
    if (!window.confirm(`“${project.name}” 프로젝트와 업로드된 사본을 모두 삭제할까요?`)) return
    try { await api.projects.remove(project.id); await onRemoved(project.id); notify('프로젝트를 삭제했습니다.') }
    catch (error) { notify(error instanceof Error ? error.message : '프로젝트 삭제에 실패했습니다.', 'error') }
  }

  if (!settings) return <div className="center-state"><LoaderCircle className="spin" /><p>설정을 불러오는 중입니다.</p></div>

  const policies: { key: keyof ProjectSettings; label: string; detail: string }[] = [
    { key: 'readPolicy', label: '파일 읽기', detail: '관련 파일 내용을 요청 맥락에 포함' },
    { key: 'createPolicy', label: '파일 생성', detail: '새 코드 또는 텍스트 파일 생성' },
    { key: 'editPolicy', label: '파일 수정', detail: '기존 파일 내용 덮어쓰기' },
    { key: 'movePolicy', label: '이동·이름 변경', detail: '상대 경로와 파일명 변경' },
    { key: 'deletePolicy', label: '파일 삭제', detail: '업로드된 작업 사본에서 제거' },
    { key: 'executePolicy', label: '코드 실행', detail: '명령 및 테스트 실행 · 현재 미지원' },
  ]

  return (
    <section className="settings-layout">
      <div className="settings-main">
        <section className="settings-card panel"><div className="settings-title"><div className="settings-icon"><HardDrive size={19} /></div><div><h2>프로젝트 정보</h2><p>사이드바와 작업공간에 표시되는 기본 정보입니다.</p></div></div><div className="form-grid"><label>이름<input value={name} onChange={event => setName(event.target.value)} /></label><label className="full">설명<textarea value={description} onChange={event => setDescription(event.target.value)} /></label></div></section>

        <section className="settings-card panel"><div className="settings-title"><div className="settings-icon"><ShieldCheck size={19} /></div><div><h2>에이전트 권한</h2><p>향후 에이전트가 파일 작업을 요청할 때 적용됩니다.</p></div></div>
          <div className="mode-grid"><label>실행 모드<select value={settings.executionMode} onChange={event => updatePolicy('executionMode', event.target.value as ExecutionMode)}><option value="CONFIRM_EVERY_STEP">매 단계 확인</option><option value="BALANCED">균형 모드</option><option value="AUTONOMOUS">자율 실행</option></select></label><label>개인정보 모드<select value={settings.privacyMode} onChange={event => updatePolicy('privacyMode', event.target.value as PrivacyMode)}><option value="LOCAL_ONLY">로컬 전용</option><option value="EXTERNAL_ALLOWED">외부 AI 전송 허용</option></select></label></div>
          <div className="policy-list">{policies.map(item => <div className="policy-row" key={item.key}><div><strong>{item.label}</strong><p>{item.detail}</p></div><select value={settings[item.key] as string} onChange={event => updatePolicy(item.key, event.target.value as PermissionPolicy)}><option value="ALLOW">{policyLabels.ALLOW}</option><option value="CONFIRM">{policyLabels.CONFIRM}</option><option value="DENY">{policyLabels.DENY}</option></select></div>)}</div>
        </section>

        <section className="danger-card panel"><div><AlertTriangle size={19} /><span><strong>프로젝트 삭제</strong><p>DB 기록과 서버의 업로드 사본을 함께 삭제합니다.</p></span></div><button className="secondary-button danger-text" onClick={remove}><Trash2 size={15} />삭제</button></section>
      </div>

      <aside className="settings-side">
        <section className="settings-card panel"><div className="settings-title compact"><div className="settings-icon"><Cloud size={18} /></div><div><h2>AI 제공자</h2><p>환경 변수 연결 상태</p></div><button className="icon-button" onClick={refreshProviders} disabled={refreshingProviders} title="제공자 상태 새로고침"><RefreshCw className={refreshingProviders ? 'spin' : ''} size={15} /></button></div><div className="provider-list">{providerState.map(provider => <div key={provider.type}><span className="provider-logo">{provider.displayName.slice(0, 1)}</span><span><strong>{provider.displayName}</strong><small>{provider.model || provider.credentialType}</small></span><i className={provider.configured ? 'ready' : ''}>{provider.configured ? <><Check size={12} />설정됨</> : '환경 설정 필요'}</i></div>)}</div></section>
        <button className="primary-button wide" onClick={save} disabled={saving || !name.trim()}><Save size={16} />{saving ? '저장 중…' : '모든 설정 저장'}</button>
      </aside>
    </section>
  )
}

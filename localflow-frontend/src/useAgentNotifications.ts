import { useEffect, useRef } from 'react'
import { api } from './api'
import type { AgentRun, Project } from './types'

const SETTING_KEY = 'localflow.browserNotifications'

export function browserNotificationsEnabled() {
  return window.localStorage.getItem(SETTING_KEY) === 'true'
}

export function saveBrowserNotifications(enabled: boolean) {
  window.localStorage.setItem(SETTING_KEY, String(enabled))
}

type NotifyStatus = 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'WAITING_APPROVAL' | 'FAILED'

const notificationCopy: Record<NotifyStatus, { title: string; body: (run: AgentRun) => string }> = {
  COMPLETED: { title: 'LocalFlow 작업 완료', body: run => run.prompt },
  PARTIALLY_COMPLETED: { title: 'LocalFlow 작업 일부 완료', body: run => run.resultMessage || run.prompt },
  WAITING_APPROVAL: { title: 'LocalFlow 승인 필요', body: run => `${run.prompt}\n파일 변경 계획을 확인해 주세요.` },
  FAILED: { title: 'LocalFlow 작업 실패', body: run => run.errorMessage || run.prompt },
}

export function useAgentNotifications(project: Project | null, enabled: boolean,
                                      onOpenRun: (runId: string) => void) {
  const previous = useRef<Map<string, AgentRun>>(new Map())
  const initializedProject = useRef<string | null>(null)

  useEffect(() => {
    if (!project || !enabled || !('Notification' in window) || Notification.permission !== 'granted') {
      previous.current.clear()
      initializedProject.current = null
      return
    }
    let disposed = false
    let timer: number | null = null

    const emit = (run: AgentRun, status: NotifyStatus) => {
      const dedupKey = `localflow.notified.${project.id}.${run.runId}.${status}`
      if (window.localStorage.getItem(dedupKey) === 'true') return
      window.localStorage.setItem(dedupKey, 'true')
      if (!document.hidden) return
      const copy = notificationCopy[status]
      const notification = new Notification(copy.title, { body: copy.body(run), tag: dedupKey })
      notification.onclick = () => {
        window.focus()
        onOpenRun(run.runId)
        notification.close()
      }
    }

    const poll = async () => {
      try {
        const active = await api.runs.active(project.id)
        if (disposed) return
        const current = new Map(active.map(run => [run.runId, run]))
        if (initializedProject.current !== project.id) {
          initializedProject.current = project.id
          previous.current = current
        } else {
          for (const run of active) {
            const oldStatus = previous.current.get(run.runId)?.status
            if (run.status === 'WAITING_APPROVAL' && oldStatus !== run.status) emit(run, 'WAITING_APPROVAL')
          }
          for (const [runId, oldRun] of previous.current) {
            if (current.has(runId) || oldRun.status === 'WAITING_APPROVAL') continue
            const finished = await api.runs.get(project.id, runId)
            if (disposed) return
            if (finished.status === 'COMPLETED' || finished.status === 'PARTIALLY_COMPLETED'
              || finished.status === 'FAILED') emit(finished, finished.status)
          }
          previous.current = current
        }
      } catch {
        // 화면 내 기존 상태 조회가 계속 동작하도록 알림 감시 실패는 조용히 재시도한다.
      } finally {
        if (!disposed) timer = window.setTimeout(poll, 2000)
      }
    }
    void poll()
    return () => {
      disposed = true
      if (timer != null) window.clearTimeout(timer)
    }
  }, [project?.id, enabled, onOpenRun])
}

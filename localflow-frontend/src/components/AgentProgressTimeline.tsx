import { CheckCircle2, Circle, LoaderCircle, PauseCircle, XCircle } from 'lucide-react'
import type { AgentRunProgress } from '../types'

type Props = {
  events: AgentRunProgress[]
  active?: boolean
}

export default function AgentProgressTimeline({ events, active = false }: Props) {
  return (
    <ol className="agent-progress-list">
      {events.map((event, index) => {
        const current = active && index === events.length - 1
        const failed = event.stage === 'FAILED'
        const cancelled = event.stage === 'CANCELLED'
        const waiting = event.stage === 'WAITING_APPROVAL'
        const completed = event.stage === 'COMPLETED' || (!current && !failed && !cancelled && !waiting)
        return (
          <li className={`${current ? 'current' : ''} ${failed ? 'failed' : ''} ${cancelled ? 'cancelled' : ''}`} key={event.id}>
            <span className="agent-progress-marker">
              {failed || cancelled ? <XCircle size={16} />
                : waiting ? <PauseCircle size={16} />
                  : current ? <LoaderCircle className="spin" size={16} />
                    : completed ? <CheckCircle2 size={16} /> : <Circle size={16} />}
            </span>
            <div>
              <p>{event.message}</p>
              <time>{new Date(event.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</time>
            </div>
          </li>
        )
      })}
    </ol>
  )
}

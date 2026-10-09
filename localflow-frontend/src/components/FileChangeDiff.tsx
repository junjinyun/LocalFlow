import { structuredPatch } from 'diff'
import type { AgentFileChangeSnapshot } from '../types'

const actionLabels: Record<AgentFileChangeSnapshot['action'], string> = {
  CREATE: '생성',
  UPDATE: '수정',
  MOVE: '이동',
  DELETE: '삭제',
}

export default function FileChangeDiff({ change }: { change: AgentFileChangeSnapshot }) {
  if (change.action === 'MOVE') {
    return (
      <div className="diff-move">
        <span>{change.path}</span><b>→</b><span>{change.destinationPath || '대상 경로 없음'}</span>
        <p>파일 내용은 유지되고 경로만 변경됩니다.</p>
      </div>
    )
  }

  const beforeAvailable = change.action === 'CREATE' || change.beforeContent != null
  const afterAvailable = change.action === 'DELETE' || change.afterContent != null
  if (!beforeAvailable || !afterAvailable) {
    return <div className="diff-unavailable">이 기록에는 비교할 파일 본문이 저장되어 있지 않습니다.</div>
  }

  const before = change.beforeContent ?? ''
  const after = change.afterContent ?? ''
  const patch = structuredPatch(
    change.path,
    change.destinationPath || change.path,
    before,
    after,
    '변경 전',
    '변경 후',
    { context: 3 },
  )
  const added = patch.hunks.reduce((count, hunk) => count + hunk.lines.filter(line => line.startsWith('+')).length, 0)
  const removed = patch.hunks.reduce((count, hunk) => count + hunk.lines.filter(line => line.startsWith('-')).length, 0)

  if (patch.hunks.length === 0) {
    return <div className="diff-unavailable">파일 내용의 변화가 없습니다.</div>
  }

  return (
    <div className="file-diff">
      <div className="diff-toolbar">
        <strong>{actionLabels[change.action]}</strong>
        <span className="added">+{added}</span>
        <span className="removed">−{removed}</span>
      </div>
      <div className="diff-scroll">
        {patch.hunks.map((hunk, hunkIndex) => {
          let oldLine = hunk.oldStart
          let newLine = hunk.newStart
          return (
            <div className="diff-hunk" key={`${hunk.oldStart}-${hunk.newStart}-${hunkIndex}`}>
              <div className="diff-hunk-header">@@ -{hunk.oldStart},{hunk.oldLines} +{hunk.newStart},{hunk.newLines} @@</div>
              {hunk.lines.map((line, lineIndex) => {
                const prefix = line.charAt(0)
                const kind = prefix === '+' ? 'added' : prefix === '-' ? 'removed' : prefix === '\\' ? 'notice' : 'context'
                const beforeLine = kind === 'added' || kind === 'notice' ? null : oldLine++
                const afterLine = kind === 'removed' || kind === 'notice' ? null : newLine++
                return (
                  <div className={`diff-line ${kind}`} key={`${hunkIndex}-${lineIndex}`}>
                    <span className="old-line">{beforeLine}</span>
                    <span className="new-line">{afterLine}</span>
                    <span className="diff-prefix">{kind === 'context' ? ' ' : prefix}</span>
                    <code>{kind === 'notice' ? line : line.slice(1)}</code>
                  </div>
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
  )
}

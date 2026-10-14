import { useMemo, useState } from 'react'
import { diffLines } from 'diff'
import type { ReactNode } from 'react'
import type { AgentFileChangeSnapshot } from '../types'

const MAX_DIFF_CHARS = 500_000
const MAX_DIFF_LINES = 20_000
const CONTEXT_LINES = 4

const actionLabels: Record<AgentFileChangeSnapshot['action'], string> = {
  CREATE: '생성', UPDATE: '수정', MOVE: '이동', DELETE: '삭제',
}

type DiffRow = { key: string; kind: 'added' | 'removed' | 'context'; beforeLine: number | null; afterLine: number | null; content: string }
type DiffBlock = { type: 'rows' | 'collapsed'; key: string; rows: DiffRow[] }

const keywords = new Set([
  'abstract', 'async', 'await', 'boolean', 'break', 'case', 'catch', 'class', 'const', 'continue',
  'default', 'delete', 'do', 'else', 'enum', 'export', 'extends', 'false', 'final', 'finally',
  'for', 'from', 'function', 'if', 'implements', 'import', 'in', 'instanceof', 'interface',
  'let', 'new', 'null', 'package', 'private', 'protected', 'public', 'record', 'return',
  'static', 'super', 'switch', 'this', 'throw', 'throws', 'true', 'try', 'type',
  'undefined', 'var', 'void', 'while', 'yield',
])

function splitLines(value: string) {
  const result = value.split('\n')
  if (result.at(-1) === '') result.pop()
  return result
}

export function buildDiffRows(before: string, after: string): DiffRow[] {
  let oldLine = 1
  let newLine = 1
  const rows: DiffRow[] = []
  diffLines(before, after).forEach((part, partIndex) => {
    const kind: DiffRow['kind'] = part.added ? 'added' : part.removed ? 'removed' : 'context'
    splitLines(part.value).forEach((content, lineIndex) => {
      const beforeLine = kind === 'added' ? null : oldLine++
      const afterLine = kind === 'removed' ? null : newLine++
      rows.push({ key: `${partIndex}-${lineIndex}`, kind, beforeLine, afterLine, content })
    })
  })
  return rows
}

function buildBlocks(rows: DiffRow[]): DiffBlock[] {
  const result: DiffBlock[] = []
  let index = 0
  while (index < rows.length) {
    const start = index
    const kind = rows[index].kind
    while (index < rows.length && rows[index].kind === kind) index++
    const group = rows.slice(start, index)
    if (kind === 'context' && group.length > CONTEXT_LINES * 2 + 1) {
      result.push({ type: 'rows', key: `before-${start}`, rows: group.slice(0, CONTEXT_LINES) })
      result.push({ type: 'collapsed', key: `collapsed-${start}`, rows: group.slice(CONTEXT_LINES, -CONTEXT_LINES) })
      result.push({ type: 'rows', key: `after-${start}`, rows: group.slice(-CONTEXT_LINES) })
    } else result.push({ type: 'rows', key: `rows-${start}`, rows: group })
  }
  return result
}

function highlighted(content: string, path: string): ReactNode {
  const extension = path.split('.').pop()?.toLowerCase()
  const supported = new Set(['java', 'kt', 'kts', 'js', 'jsx', 'ts', 'tsx', 'json', 'css', 'scss', 'html', 'xml', 'yml', 'yaml', 'py', 'sql', 'md'])
  if (!extension || !supported.has(extension)) return content
  if (/^\s*(\/\/|#|<!--|\*)/.test(content)) return <span className="syntax-comment">{content}</span>
  const tokens = content.split(/("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_$][\w$]*\b)/g)
  return tokens.map((token, index) => {
    let className = ''
    if (/^["'`]/.test(token)) className = 'syntax-string'
    else if (/^\d/.test(token)) className = 'syntax-number'
    else if (keywords.has(token)) className = 'syntax-keyword'
    return className ? <span className={className} key={index}>{token}</span> : token
  })
}

export default function FileChangeDiff({ change }: { change: AgentFileChangeSnapshot }) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const before = change.beforeContent ?? ''
  const after = change.afterContent ?? ''
  const rows = useMemo(() => buildDiffRows(before, after), [before, after])
  const blocks = useMemo(() => buildBlocks(rows), [rows])

  if (change.action === 'MOVE') return <div className="diff-move"><span>{change.path}</span><b>→</b><span>{change.destinationPath || '대상 경로 없음'}</span><p>파일 내용은 유지되고 경로만 변경됩니다.</p></div>
  const beforeAvailable = change.action === 'CREATE' || change.beforeContent != null
  const afterAvailable = change.action === 'DELETE' || change.afterContent != null
  if (!beforeAvailable || !afterAvailable) return <div className="diff-unavailable">이 기록에는 비교할 파일 본문이 저장되어 있지 않습니다.</div>
  if (before.length + after.length > MAX_DIFF_CHARS || rows.length > MAX_DIFF_LINES) return <div className="diff-unavailable">파일이 너무 커서 브라우저 Diff 표시를 생략했습니다. 원본 파일을 내려받아 확인해 주세요.</div>
  const added = rows.filter(row => row.kind === 'added').length
  const removed = rows.filter(row => row.kind === 'removed').length
  if (added === 0 && removed === 0) return <div className="diff-unavailable">파일 내용의 변화가 없습니다.</div>

  const renderRow = (row: DiffRow) => <div className={`diff-line ${row.kind}`} key={row.key}>
    <span className="old-line">{row.beforeLine}</span><span className="new-line">{row.afterLine}</span>
    <span className="diff-prefix">{row.kind === 'added' ? '+' : row.kind === 'removed' ? '−' : ' '}</span>
    <code>{highlighted(row.content, change.destinationPath || change.path)}</code>
  </div>

  return <div className="file-diff">
    <div className="diff-toolbar"><strong>{actionLabels[change.action]}</strong><span className="added">+{added}</span><span className="removed">−{removed}</span></div>
    <div className="diff-scroll">{blocks.map(block => block.type === 'rows' || expanded.has(block.key)
      ? <div className="diff-block" key={block.key}>{block.rows.map(renderRow)}</div>
      : <button className="diff-collapsed" key={block.key} onClick={() => setExpanded(current => new Set(current).add(block.key))}>미변경 {block.rows.length}줄 펼치기</button>)}</div>
  </div>
}

import { useEffect, useState } from 'react';
import { Bot, CircleStop, Play, RefreshCw, RotateCcw } from 'lucide-react';
import { api } from './api';
import type { Factory, NexusTask, NexusTaskLog, NexusTaskType } from './types';

export function TaskOperationsPanel({ factories, tasks, onChanged }: { factories: Factory[]; tasks: NexusTask[]; onChanged: () => Promise<void> }) {
  const [title, setTitle] = useState('제조 운영 분석');
  const [type, setType] = useState<NexusTaskType>('MANUFACTURING_QUERY');
  const [factoryId, setFactoryId] = useState('');
  const [question, setQuestion] = useState('생산, 품질, 재고, 설비, 출하 상태를 종합 분석해줘');
  const [selectedId, setSelectedId] = useState<string | null>(tasks[0]?.id ?? null);
  const [logs, setLogs] = useState<NexusTaskLog[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { if (!selectedId && tasks.length) setSelectedId(tasks[0].id); }, [selectedId, tasks]);
  useEffect(() => { if (!selectedId) { setLogs([]); return; } void api.taskLogs(selectedId).then(setLogs).catch(() => setLogs([])); }, [selectedId, tasks]);
  const act = async (action: () => Promise<unknown>) => { setPending(true); try { await action(); await onChanged(); setError(''); } catch (cause) { setError(cause instanceof Error ? cause.message : '작업 요청에 실패했습니다.'); } finally { setPending(false); } };
  const create = async () => { if (!title.trim() || (type === 'MANUFACTURING_QUERY' && !question.trim())) return; await act(async () => { const task = await api.createTask({ title, type, factoryId: factoryId || undefined, question: type === 'MANUFACTURING_QUERY' ? question : undefined, requestedBy: 'operator' }); setSelectedId(task.id); }); };
  const selected = tasks.find(task => task.id === selectedId) ?? null;
  return <div className="task-workspace">
    <section className="panel"><div className="panel-title"><h2><Bot size={17} />운영 작업 생성</h2></div><div className="task-form">
      <label>작업 이름<input value={title} onChange={event => setTitle(event.target.value)} /></label>
      <label>실행 유형<select value={type} onChange={event => setType(event.target.value as NexusTaskType)}><option value="MANUFACTURING_QUERY">Manufacturing Agent 분석</option><option value="SIMULATOR_TICK">Simulator Tick 생성</option></select></label>
      <label>공장<select value={factoryId} onChange={event => setFactoryId(event.target.value)}><option value="">전체 공장</option>{factories.map(factory => <option key={factory.id} value={factory.id}>{factory.name}</option>)}</select></label>
      {type === 'MANUFACTURING_QUERY' && <label>질문<textarea rows={5} value={question} onChange={event => setQuestion(event.target.value)} /></label>}
      <button disabled={pending} onClick={() => void create()}>초안 만들기</button>{error && <p className="error-text" role="alert">{error}</p>}
    </div></section>
    <section className="panel"><div className="panel-title"><h2>작업 상태</h2><span>{tasks.length}</span></div><div className="task-list">{tasks.length === 0 ? <div className="empty-state">생성된 작업이 없습니다.</div> : tasks.map(task => <button className={`task-row ${selectedId === task.id ? 'selected' : ''}`} key={task.id} onClick={() => setSelectedId(task.id)}><span><strong>{task.title}</strong><small>{task.id} · {task.type}</small></span><TaskStatus value={task.status} /></button>)}</div></section>
    <section className="panel full"><div className="panel-title"><h2>작업 상세와 실행 근거</h2>{selected && <TaskStatus value={selected.status} />}</div>{!selected ? <div className="empty-state">확인할 작업을 선택하세요.</div> : <>
      <div className="task-detail-header"><div><strong>{selected.title}</strong><p>{selected.resultSummary ?? selected.errorMessage ?? selected.question ?? '실행 대기 중'}</p><small>시도 {selected.attemptCount}/{selected.maxAttempts} · 요청자 {selected.requestedBy}</small></div><div className="inline-actions">
        {['DRAFT', 'RETRY_REQUESTED'].includes(selected.status) && <button disabled={pending} onClick={() => void act(() => api.runTask(selected.id))}><Play size={15} />Workflow 시작</button>}
        {selected.status === 'WAITING_APPROVAL' && <button disabled={pending} onClick={() => void act(() => api.syncTask(selected.id))}><RefreshCw size={15} />승인 동기화</button>}
        {!['SUCCESS', 'FAILED', 'REJECTED', 'CANCELLED'].includes(selected.status) && <button className="reject" disabled={pending} onClick={() => void act(() => api.cancelTask(selected.id))}><CircleStop size={15} />중단</button>}
        {['FAILED', 'CANCELLED', 'REJECTED'].includes(selected.status) && selected.attemptCount < selected.maxAttempts && <button disabled={pending} onClick={() => void act(() => api.retryTask(selected.id))}><RotateCcw size={15} />재시도 요청</button>}
      </div></div>
      <dl className="summary-list"><Summary label="Correlation ID" value={selected.correlationId} /><Summary label="Workflow ID" value={selected.workflowId ?? '-'} /><Summary label="Approval ID" value={selected.approvalId ?? '-'} /><Summary label="Confidence" value={selected.confidence == null ? '-' : `${Math.round(selected.confidence * 100)}%`} /></dl>
      <div className="grid"><article><h3>Evidence</h3><pre>{JSON.stringify(selected.evidence, null, 2)}</pre></article><article><h3>Recommendation</h3><ul>{selected.recommendation.map(item => <li key={item}>{item}</li>)}</ul></article></div>
      <div className="task-logs">{logs.length === 0 ? <div className="empty-state">아직 실행 로그가 없습니다.</div> : logs.map(log => <div key={log.id} className={`task-log ${log.level.toLowerCase()}`}><time>{formatDate(log.createdAt)}</time><strong>{log.level}</strong><span>{log.message}</span></div>)}</div>
    </>}</section>
  </div>;
}

function Summary({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
function TaskStatus({ value }: { value: string }) { const tone = value === 'FAILED' || value === 'REJECTED' ? 'danger' : ['DRAFT', 'PENDING', 'ANALYZING', 'WAITING_APPROVAL', 'RUNNING', 'VERIFYING', 'RETRY_REQUESTED', 'CANCELLED'].includes(value) ? 'warning' : 'success'; return <span className={`status-badge ${tone}`}>{value}</span>; }
function formatDate(value: string) { return new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value)); }

import { useEffect, useState } from 'react';
import { Bot, CircleStop, Play, RefreshCw, RotateCcw } from 'lucide-react';
import { api } from './api';
import { useI18n } from './i18n';
import type { Factory, NexusTask, NexusTaskLog, NexusTaskType } from './types';

export function TaskOperationsPanel({ factories, tasks, onChanged }: { factories: Factory[]; tasks: NexusTask[]; onChanged: () => Promise<void> }) {
  const { t, formatDate } = useI18n();
  const [title, setTitle] = useState(() => t('task.defaultTitle'));
  const [type, setType] = useState<NexusTaskType>('MANUFACTURING_QUERY');
  const [factoryId, setFactoryId] = useState('');
  const [question, setQuestion] = useState(() => t('task.defaultQuestion'));
  const [selectedId, setSelectedId] = useState<string | null>(tasks[0]?.id ?? null);
  const [logs, setLogs] = useState<NexusTaskLog[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { if (!selectedId && tasks.length) setSelectedId(tasks[0].id); }, [selectedId, tasks]);
  useEffect(() => { if (!selectedId) { setLogs([]); return; } void api.taskLogs(selectedId).then(setLogs).catch(() => setLogs([])); }, [selectedId, tasks]);
  const act = async (action: () => Promise<unknown>) => { setPending(true); try { await action(); await onChanged(); setError(''); } catch (cause) { setError(cause instanceof Error ? cause.message : t('task.requestFailed')); } finally { setPending(false); } };
  const create = async () => { if (!title.trim() || (type === 'MANUFACTURING_QUERY' && !question.trim())) return; await act(async () => { const task = await api.createTask({ title, type, factoryId: factoryId || undefined, question: type === 'MANUFACTURING_QUERY' ? question : undefined, requestedBy: 'operator' }); setSelectedId(task.id); }); };
  const selected = tasks.find(task => task.id === selectedId) ?? null;
  return <div className="task-workspace">
    <section className="panel"><div className="panel-title"><h2><Bot size={17} />{t('task.create')}</h2></div><div className="task-form">
      <label>{t('task.title')}<input value={title} onChange={event => setTitle(event.target.value)} /></label>
      <label>{t('task.type')}<select value={type} onChange={event => setType(event.target.value as NexusTaskType)}><option value="MANUFACTURING_QUERY">{t('task.type.manufacturingQuery')}</option><option value="SIMULATOR_TICK">{t('task.type.simulatorTick')}</option></select></label>
      <label>{t('task.factory')}<select value={factoryId} onChange={event => setFactoryId(event.target.value)}><option value="">{t('common.allFactories')}</option>{factories.map(factory => <option key={factory.id} value={factory.id}>{factory.name}</option>)}</select></label>
      {type === 'MANUFACTURING_QUERY' && <label>{t('task.question')}<textarea rows={5} value={question} onChange={event => setQuestion(event.target.value)} /></label>}
      <button disabled={pending} onClick={() => void create()}>{t('task.createDraft')}</button>{error && <p className="error-text" role="alert">{error}</p>}
    </div></section>
    <section className="panel"><div className="panel-title"><h2>{t('task.status')}</h2><span>{tasks.length}</span></div><div className="task-list">{tasks.length === 0 ? <div className="empty-state">{t('task.noTasks')}</div> : tasks.map(task => <button className={`task-row ${selectedId === task.id ? 'selected' : ''}`} key={task.id} onClick={() => setSelectedId(task.id)}><span><strong>{task.title}</strong><small>{task.id} · {task.type}</small></span><TaskStatus value={task.status} /></button>)}</div></section>
    <section className="panel full"><div className="panel-title"><h2>{t('task.detail')}</h2>{selected && <TaskStatus value={selected.status} />}</div>{!selected ? <div className="empty-state">{t('task.selectTask')}</div> : <>
      <div className="task-detail-header"><div><strong>{selected.title}</strong><p>{selected.resultSummary ?? selected.errorMessage ?? selected.question ?? t('task.waiting')}</p><small>{t('task.attempt', { attempt: selected.attemptCount, max: selected.maxAttempts, requestedBy: selected.requestedBy })}</small></div><div className="inline-actions">
        {['DRAFT', 'RETRY_REQUESTED'].includes(selected.status) && <button disabled={pending} onClick={() => void act(() => api.runTask(selected.id))}><Play size={15} />{t('task.startWorkflow')}</button>}
        {selected.status === 'WAITING_APPROVAL' && <button disabled={pending} onClick={() => void act(() => api.syncTask(selected.id))}><RefreshCw size={15} />{t('task.syncApproval')}</button>}
        {!['SUCCESS', 'FAILED', 'REJECTED', 'CANCELLED'].includes(selected.status) && <button className="reject" disabled={pending} onClick={() => void act(() => api.cancelTask(selected.id))}><CircleStop size={15} />{t('task.cancel')}</button>}
        {['FAILED', 'CANCELLED', 'REJECTED'].includes(selected.status) && selected.attemptCount < selected.maxAttempts && <button disabled={pending} onClick={() => void act(() => api.retryTask(selected.id))}><RotateCcw size={15} />{t('task.retry')}</button>}
      </div></div>
      <dl className="summary-list"><Summary label="Correlation ID" value={selected.correlationId} /><Summary label="Workflow ID" value={selected.workflowId ?? '-'} /><Summary label="Approval ID" value={selected.approvalId ?? '-'} /><Summary label={t('common.confidence')} value={selected.confidence == null ? '-' : `${Math.round(selected.confidence * 100)}%`} /></dl>
      <div className="grid"><article><h3>{t('task.evidence')}</h3><pre>{JSON.stringify(selected.evidence, null, 2)}</pre></article><article><h3>{t('task.recommendation')}</h3><ul>{selected.recommendation.map(item => <li key={item}>{item}</li>)}</ul></article></div>
      <div className="task-logs">{logs.length === 0 ? <div className="empty-state">{t('task.noLogs')}</div> : logs.map(log => <div key={log.id} className={`task-log ${log.level.toLowerCase()}`}><time>{formatDate(log.createdAt)}</time><strong>{log.level}</strong><span>{log.message}</span></div>)}</div>
    </>}</section>
  </div>;
}

function Summary({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
function TaskStatus({ value }: { value: string }) { const { statusLabel } = useI18n(); const tone = value === 'FAILED' || value === 'REJECTED' ? 'danger' : ['DRAFT', 'PENDING', 'ANALYZING', 'WAITING_APPROVAL', 'RUNNING', 'VERIFYING', 'RETRY_REQUESTED', 'CANCELLED'].includes(value) ? 'warning' : 'success'; return <span className={`status-badge ${tone}`} title={value}>{statusLabel(value)}</span>; }


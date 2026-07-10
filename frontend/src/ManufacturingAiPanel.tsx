import { Bot, CheckCircle2, Search, Sparkles } from 'lucide-react';
import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import { api } from './api';
import { useI18n } from './i18n';
import type { AiQueryResponse, Factory } from './types';

export function ManufacturingAiPanel({ factories, onChanged }: { factories: Factory[]; onChanged: () => Promise<void> }) {
  const { t, formatDate } = useI18n();
  const [question, setQuestion] = useState(() => t('ai.defaultQuestion'));
  const [factoryId, setFactoryId] = useState('');
  const [result, setResult] = useState<AiQueryResponse | null>(null);
  const [history, setHistory] = useState<AiQueryResponse[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  const loadHistory = async () => setHistory(await api.aiQueries());
  useEffect(() => { void loadHistory().catch(() => setHistory([])); }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim()) return;
    setPending(true);
    setError('');
    try {
      const response = await api.aiQuery({ question: question.trim(), factoryId: factoryId || undefined, requestedBy: 'operator' });
      setResult(response);
      await Promise.all([loadHistory(), onChanged()]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('ai.requestFailed'));
    } finally {
      setPending(false);
    }
  };

  return <div className="ai-layout">
    <section className="panel ai-query-panel">
      <div className="panel-title"><h2><Bot size={18} />{t('ai.orchestrator')}</h2></div>
      <form className="ai-form" onSubmit={submit}>
        <label>{t('ai.factoryTarget')}<select value={factoryId} onChange={(event) => setFactoryId(event.target.value)}><option value="">{t('common.allFactories')}</option>{factories.map((factory) => <option key={factory.id} value={factory.id}>{factory.name} ({factory.id})</option>)}</select></label>
        <label>{t('ai.question')}<textarea value={question} onChange={(event) => setQuestion(event.target.value)} rows={4} /></label>
        <button type="submit" disabled={pending || !question.trim()}><Sparkles size={17} />{pending ? t('ai.running') : t('ai.submit')}</button>
      </form>
      {error && <div className="notice" role="alert">{error}</div>}
    </section>

    <section className="panel ai-result-panel">
      <div className="panel-title"><h2><CheckCircle2 size={18} />{t('ai.resultTitle')}</h2>{result && <span>{result.executionStatus}</span>}</div>
      {!result ? <div className="empty-state">{t('ai.emptyResult')}</div> : <AiResult result={result} />}
    </section>

    <section className="panel full">
      <div className="panel-title"><h2><Search size={18} />{t('ai.history')}</h2><span>{history.length}</span></div>
      {history.length === 0 ? <div className="empty-state">{t('ai.noHistory')}</div> : <div className="table-wrap"><table><thead><tr><th>{t('table.time')}</th><th>{t('table.question')}</th><th>{t('table.intent')}</th><th>{t('table.agent')}</th><th>{t('table.status')}</th><th>{t('common.confidence')}</th><th>{t('table.rpa')}</th></tr></thead><tbody>{history.slice(0, 10).map((item) => <tr key={item.queryId}><td>{formatDate(item.createdAt)}</td><td>{item.question}</td><td>{item.routedIntents.join(', ')}</td><td>{item.invokedAgents.join(', ') || '-'}</td><td>{item.executionStatus}</td><td>{Math.round(item.confidence * 100)}%</td><td>{item.rpaTaskId ?? '-'}</td></tr>)}</tbody></table></div>}
    </section>
  </div>;
}

function AiResult({ result }: { result: AiQueryResponse }) {
  const { t, statusLabel } = useI18n();
  return <div className="ai-result">
    <div className="tag-row">{result.routedIntents.map((intent) => <span className="status-badge success" key={intent}>{intent}</span>)}{result.invokedAgents.map((agent) => <span className="agent-tag" key={agent}>{agent}</span>)}</div>
    <p className="ai-answer">{result.answer}</p>
    <div className="agent-results">{result.agentResults.map((agent) => <article key={agent.agentName}><div className="card-heading"><strong>{agent.agentName}</strong><span className={`status-badge ${agent.status === 'FAILED' ? 'danger' : agent.status === 'INSUFFICIENT_DATA' ? 'warning' : 'success'}`} title={agent.status}>{statusLabel(agent.status)}</span></div><p>{agent.summary}</p><small>{t('common.confidence')} {Math.round(agent.confidence * 100)}% · {t('common.ms', { value: agent.executionTimeMs })}</small>{agent.errorMessage && <p className="error-text">{agent.errorMessage}</p>}</article>)}</div>
    <div className="ai-columns"><div><h3>{t('ai.evidence')}</h3>{result.evidence.length ? <ul>{result.evidence.map((item, index) => <li key={`${item.type}-${index}`}><strong>{item.description}</strong>: {item.value} <small>({item.source})</small></li>)}</ul> : <p>{t('ai.noEvidence')}</p>}</div><div><h3>{t('ai.actions')}</h3>{result.recommendedActions.length ? <ul>{result.recommendedActions.map((action) => <li key={action}>{action}</li>)}</ul> : <p>{t('ai.noAction')}</p>}{result.rpaTaskId && <p className="rpa-created">{t('ai.rpaCreated')}: {result.rpaTaskId}{result.approvalRequired ? ` · ${t('ai.approvalRequired')}` : ''}</p>}</div></div>
  </div>;
}


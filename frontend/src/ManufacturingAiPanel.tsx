import { Bot, CheckCircle2, Search, Sparkles } from 'lucide-react';
import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import { api } from './api';
import type { AiQueryResponse, Factory } from './types';

export function ManufacturingAiPanel({ factories, onChanged }: { factories: Factory[]; onChanged: () => Promise<void> }) {
  const [question, setQuestion] = useState('생산량 감소 원인과 설비 이상 여부를 분석해줘');
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
      setError(cause instanceof Error ? cause.message : 'AI 분석 요청 실패');
    } finally {
      setPending(false);
    }
  };

  return <div className="ai-layout">
    <section className="panel ai-query-panel">
      <div className="panel-title"><h2><Bot size={18} />Manufacturing Orchestrator</h2></div>
      <form className="ai-form" onSubmit={submit}>
        <label>분석 대상 공장<select value={factoryId} onChange={(event) => setFactoryId(event.target.value)}><option value="">전체 공장</option>{factories.map((factory) => <option key={factory.id} value={factory.id}>{factory.name} ({factory.id})</option>)}</select></label>
        <label>운영 질문<textarea value={question} onChange={(event) => setQuestion(event.target.value)} rows={4} /></label>
        <button type="submit" disabled={pending || !question.trim()}><Sparkles size={17} />{pending ? 'Agent 분석 중' : '질문 실행'}</button>
      </form>
      {error && <div className="notice" role="alert">{error}</div>}
    </section>

    <section className="panel ai-result-panel">
      <div className="panel-title"><h2><CheckCircle2 size={18} />통합 분석 결과</h2>{result && <span>{result.executionStatus}</span>}</div>
      {!result ? <div className="empty-state">제조 운영 질문을 실행하면 Agent 분석 결과가 표시됩니다.</div> : <AiResult result={result} />}
    </section>

    <section className="panel full">
      <div className="panel-title"><h2><Search size={18} />최근 Query History</h2><span>{history.length}</span></div>
      {history.length === 0 ? <div className="empty-state">저장된 AI Query가 없습니다.</div> : <div className="table-wrap"><table><thead><tr><th>시각</th><th>질문</th><th>Intent</th><th>Agent</th><th>상태</th><th>신뢰도</th><th>RPA</th></tr></thead><tbody>{history.slice(0, 10).map((item) => <tr key={item.queryId}><td>{formatDate(item.createdAt)}</td><td>{item.question}</td><td>{item.routedIntents.join(', ')}</td><td>{item.invokedAgents.join(', ') || '-'}</td><td>{item.executionStatus}</td><td>{Math.round(item.confidence * 100)}%</td><td>{item.rpaTaskId ?? '-'}</td></tr>)}</tbody></table></div>}
    </section>
  </div>;
}

function AiResult({ result }: { result: AiQueryResponse }) {
  return <div className="ai-result">
    <div className="tag-row">{result.routedIntents.map((intent) => <span className="status-badge success" key={intent}>{intent}</span>)}{result.invokedAgents.map((agent) => <span className="agent-tag" key={agent}>{agent}</span>)}</div>
    <p className="ai-answer">{result.answer}</p>
    <div className="agent-results">{result.agentResults.map((agent) => <article key={agent.agentName}><div className="card-heading"><strong>{agent.agentName}</strong><span className={`status-badge ${agent.status === 'FAILED' ? 'danger' : agent.status === 'INSUFFICIENT_DATA' ? 'warning' : 'success'}`}>{agent.status}</span></div><p>{agent.summary}</p><small>confidence {Math.round(agent.confidence * 100)}% · {agent.executionTimeMs}ms</small>{agent.errorMessage && <p className="error-text">{agent.errorMessage}</p>}</article>)}</div>
    <div className="ai-columns"><div><h3>근거 데이터</h3>{result.evidence.length ? <ul>{result.evidence.map((item, index) => <li key={`${item.type}-${index}`}><strong>{item.description}</strong>: {item.value} <small>({item.source})</small></li>)}</ul> : <p>판단할 데이터가 부족함</p>}</div><div><h3>권장 조치</h3>{result.recommendedActions.length ? <ul>{result.recommendedActions.map((action) => <li key={action}>{action}</li>)}</ul> : <p>즉시 조치 없음</p>}{result.rpaTaskId && <p className="rpa-created">RPA 생성: {result.rpaTaskId}{result.approvalRequired ? ' · 승인 필요' : ''}</p>}</div></div>
  </div>;
}

function formatDate(value: string) { return new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)); }

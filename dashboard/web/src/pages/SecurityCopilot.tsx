import { RISK_THRESHOLDS } from '../config/risk';
import React from 'react';
import { Brain, ShieldCheck, AlertTriangle, CheckCircle2, FileLock2, RefreshCw, Sparkles } from 'lucide-react';
import { apiGet, apiPost } from '../api/client';

const riskClass=(n:number)=>n >= RISK_THRESHOLDS.critical
    ? 'critical'
    : n >= RISK_THRESHOLDS.high
      ? 'high'
      : n >= RISK_THRESHOLDS.medium
        ? 'medium'
        : 'low';
const Status=({value}:{value:any})=>
<span className={`status ${String(value).toLowerCase()}`}>{value}
</span>;
const Metric=({label,value}:{label:string,value:any})=>
<div className="metric">
<div className="metric-icon">
<ShieldCheck size={
    17}/>
</div>
<div>
<small>{label}
</small>
<b>{value}
</b>
</div>
</div>;

export default function SecurityCopilot(){
const [data,setData]=React.useState<any>(null),[scenario,
setScenario]=React.useState('high-value AI agent payment transfer'),
 [draft,setDraft]=React.useState<any>(null),[err,setErr]=React.useState(''),
 [busy,setBusy]=React.useState(false);
 const load=async()=>{setBusy(true);
     try{setData(await apiGet('/v1/security/copilot/analysis?windowMinutes=120'));
         setErr('')}catch(e:any){setErr(e.message)}finally{setBusy(false)}};
 React.useEffect(()=>{load()},[]);
 const recommend=async()=>{try{setDraft(await apiPost('/v1/security/copilot/policy-recommendation',
         {scenario}))}catch(e:any){setErr(e.message)}};
 return <div>
  <div className="page-header">
<div>
<h2>AI Security Copilot</h2>
<p>Security Graph,
  Policy, Incident와 Runtime evidence를 근거로 위험을 분석하고 정책 초안을 제안합니다.</p>
</div>
<button onClick={
      load} disabled={busy}>
<RefreshCw size={14}/> {busy?'Analyzing…':'Re-analyze'}
</button>
</div>
  {err&&<div className="error-banner">
<AlertTriangle size={17}/>
<div>
<b>Copilot request failed</b>
<span>{
          err}
</span>
</div>
</div>}
  {data&&<>
   <div className="metric-grid">
<Metric label="Security posture" value={
       data.posture}/>
<Metric label="Risk score" value={Number(data.riskScore).toFixed(1)}
   />
<Metric label="Active policies" value={data.activePolicies}/>
<Metric label="Critical graph nodes" value={
       data.criticalGraphNodes}/>
<Metric label="Open cases in window" value={
       data.recentCases}/>
</div>
   <div className="grid-2">
    <section className="panel">
<div className="panel-head">
<h3>
<Brain size={
        17}/> Evidence-grounded findings</h3>
</div>{(data.findings||[]).length?<div className="remediation-list">{
            data.findings.map((f:any)=>
<div className="remediation-card" key={f.code}
            >
<div>
<Status value={f.severity}/>
<b>{f.title}
</b>
<small>{f.code} · {f.source}
</small>
</div>
<p>{f.recommendation}
</p>
<code>evidence count: {f.count}
</code>
</div>)}
</div>:<div className="empty">
<CheckCircle2 size={25}/>
<b>No material findings</b>
<small>The current evidence window has no Copilot findings.</small>
</div>}
</section>
    <section className="panel">
<div className="panel-head">
<h3>
<Sparkles size={
        17}/> Policy Recommendation</h3>
</div>
<label className="field">
<span>Threat / scenario</span>
<input value={
        scenario} onChange={e=>setScenario(e.target.value)}/>
</label>
<button className="primary" onClick={
        recommend}>
<Sparkles size={14}/> Generate policy draft</button>{draft&&<div className="copilot-draft">
<div className="feature-grid">
<span>
<b>Policy</b> {
            draft.name}
</span>
<span>
<b>Confidence</b> {Math.round(Number(draft.confidence)*100)}
        %</span>
<span>
<b>Type</b> {draft.recommendationType}
</span>
</div>
<pre className="json">{
            draft.policyText}
</pre>
<h4>Reasoning</h4>
<ul>{(draft.reasoning||[]).map((x:string)=>
<li key={
                x}>{x}
</li>)}
</ul>
<p className="muted small">This is a draft only. It must go through Validate → Simulate → Diff → Approval
→ Canary → Publish.</p>
</div>}
</section>
   </div>
   <section className="panel">
<div className="panel-head">
<h3>AI safety boundary</h3>
</div>
<div className="policy-flow">
<span>Evidence</span>
<i>→</i>
<span>Analyze</span>
<i>→</i>
<span>Recommend</span>
<i>→</i>
<span>Human Review</span>
<i>→</i>
<span className="final">Policy / Response Control Plane</span>
</div>
<div className="feature-grid">
<span>✓ No autonomous policy publish</span>
<span>✓ No autonomous agent isolation</span>
<span>✓ Evidence-backed recommendations</span>
<span>✓ Existing approval / audit boundary preserved</span>
</div>
</section>
  </>}
</div>
}

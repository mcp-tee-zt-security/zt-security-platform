import React from 'react';
import { History, Play, ShieldAlert, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { apiPost } from '../api/client';
import { POLICY_EXAMPLE_AMOUNT } from '../config/risk';
const sample=`policy "ai_agent_high_value_transfer_guard" {
  priority 20
  effect step_up
  description "Shadow-test high-value AI agent transfers"
  mode "enforce"
  tags ["ai-agent", "payment", "shadow"]
  principal.type == "AI_AGENT"
  action == "payment.transfer"
  resource.type == "bank_account"
  condition {
    context.amount > ${POLICY_EXAMPLE_AMOUNT} or risk.score >= 70
  }
}`;
const Pill=({v}:{v:string})=>
<span className={`status ${v.toLowerCase()}`}>{v}
</span>;
export default function ShadowReplay({policyText,onPolicyTextChange}:{policyText?:string;onPolicyTextChange?:(text:string)=>void}={}){
 const [localText,setLocalText]=React.useState(sample),[days,setDays]=React.useState(1),
 [max,setMax]=React.useState(1000),[data,setData]=React.useState<any>(null),
 [busy,setBusy]=React.useState(false),[err,setErr]=React.useState('');
 const text=policyText??localText;
 const setText=(value:string)=>{if(onPolicyTextChange)onPolicyTextChange(value);else setLocalText(value)};
 React.useEffect(()=>{setData(null);setErr('')},[text]);
 const latestText=React.useRef(text);latestText.current=text;
 const run=async()=>{setBusy(true);
     setErr('');
     setData(null);
     try{const to=new Date(),
         from=new Date(to.getTime()-days*86400000);
         const result=await apiPost('/v1/security/shadow-replay/run',
         {policyText:text,from:from.toISOString(),to:to.toISOString(),maxEvents:max}
         );if(latestText.current===text)setData(result)}catch(e:any){if(latestText.current===text)setErr(e.message)}finally{setBusy(false)}};
 return <div>
<div className="page-header">
<div>
<h2>Historical Shadow Replay</h2>
<p>과거 Runtime Events에 제안 정책을 가상 적용합니다. 실제 enforcement와 데이터 변경은 발생하지 않습니다.</p>
</div>
<button className="primary" onClick={
     run} disabled={busy}>
<Play size={14}/>{busy?'Replaying…':'Run shadow replay'}
</button>
</div>
 {err&&<div className="error-banner">
<AlertTriangle size={17}/>
<div>
<b>Replay failed</b>
<span>{err}
</span>
</div>
</div>}
<div className="grid-2">
<section className="panel">
<div className="panel-head">
<h3>
<History size={
     17}/> Proposed policy</h3>
<span className="muted small">SHADOW ONLY</span>
</div>
<textarea className="code-editor" value={
     text} onChange={e=>setText(e.target.value)} rows={20}/>
<div className="feature-grid">
<label>Window <select value={
     days} onChange={e=>setDays(Number(e.target.value))}>
<option value={1}>24 hours</option>
<option value={
     3}>3 days</option>
<option value={7}>7 days</option>
</select>
</label>
<label>Max events <select value={
     max} onChange={e=>setMax(Number(e.target.value))}>
<option>1000</option>
<option>5000</option>
<option>10000</option>
</select>
</label>
</div>
</section>
 <section className="panel">
<div className="panel-head">
<h3>
<ShieldAlert size={
     17}/> Replay impact</h3>
</div>{!data?<div className="empty">
<History size={
         25}/>
<b>Run a shadow replay</b>
<small>Historical decisions will be compared with the proposed policy.</small>
</div>:<>
<div className="metric-grid">
<Metric label="Events" value={
         data.eventCount}/>
<Metric label="Changed" value={data.changedDecisions}
     />
<Metric label="New DENY" value={data.newDenies}/>
<Metric label="New STEP_UP" value={
         data.newStepUps}/>
<Metric label="False-positive candidates" value={data.falsePositiveCandidates}
     />
<Metric label="Security improvement" value={`${data.securityImprovementPct}
         %`}/>
</div>
<div className="feature-grid">
<span>
<b>Business impact</b> {
         data.businessImpactScore}
</span>
<span>
<b>Policy hash</b> <code>{String(data.policyHash).slice(0,
         16)}…</code>
</span>
<span>
<b>Result hash</b> <code>{String(data.resultHash).slice(0,
         16)}…</code>
</span>
<span>
<b>Status</b> <Pill v={data.status}/>
</span>
</div>
</>}
</section>
</div>
 {data&&<section className="panel">
<div className="panel-head">
<h3>Historical decision diff</h3>
<span className="muted small">Policy-only shadow evaluation</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Time</th>
<th>Agent</th>
<th>Action</th>
<th>Resource</th>
<th>Baseline</th>
<th>Shadow</th>
<th>FP candidate</th>
</tr>
</thead>
<tbody>{
         (data.rows||[]).map((x:any)=>
<tr key={x.eventId}>
<td>{new Date(x.createdAt).toLocaleString()}
</td>
<td>{x.agent}
</td>
<td>
<code>{x.action}
</code>
</td>
<td>{x.resourceType}
         :{x.resourceId}
</td>
<td>
<Pill v={x.baseline}/>
</td>
<td>
<Pill v={x.shadow}
         />
</td>
<td>{x.falsePositiveCandidate?<AlertTriangle size={16}/>:<CheckCircle2 size={
                 16}/>}
</td>
</tr>)}
</tbody>
</table>
</div>
</section>}
<section className="panel">
<div className="panel-head">
<h3>Safe deployment boundary</h3>
</div>
<div className="policy-flow">
<span>Runtime Events</span>
<i>→</i>
<span>Shadow Replay</span>
<i>→</i>
<span>Risk / Business Impact</span>
<i>→</i>
<span className="final">Human Approval</span>
</div>
<div className="feature-grid">
<span>✓ No policy mutation</span>
<span>✓ No runtime action</span>
<span>✓ Replay result hash</span>
<span>✓ False-positive candidates surfaced</span>
<span>✓ Existing Policy Lifecycle remains authoritative</span>
</div>
</section>
 </div>
}
function Metric({label,value}:{label:string,value:any}){return <div className="metric">
<div>
<small>{
        label}
</small>
<b>{value}
</b>
</div>
</div>}

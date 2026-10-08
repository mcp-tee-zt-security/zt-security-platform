import React from 'react';
import { GitCompareArrows, ShieldAlert, Play, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { apiPost } from '../api/client';
import { POLICY_EXAMPLE_AMOUNT } from '../config/risk';

const sample=`policy "ai_agent_high_value_transfer_guard" {
  priority 20
  effect step_up
  description "Step up high-value AI agent transfers"
  mode "enforce"
  tags ["ai-agent", "payment", "recommended"]
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
export default function PolicyWhatIf(){
 const [text,setText]=React.useState(sample),[data,setData]=React.useState<any>(null),
 [busy,setBusy]=React.useState(false),[err,setErr]=React.useState('');
 const run=async()=>{setBusy(true);
     setErr('');
     try{setData(await apiPost('/v1/security/what-if/simulate',
         {policyText:text}))}catch(e:any){setErr(e.message)}finally{setBusy(false)}
 };
 return <div>
  <div className="page-header">
<div>
<h2>Policy What-if Simulation</h2>
<p>정책을 실제 enforcement에 반영하기 전에 현재 정책과 비교하여 보안 영향을 계산합니다.</p>
</div>
<button className="primary" onClick={
      run} disabled={busy}>
<Play size={14}/> {busy?'Simulating…':'Run simulation'}
</button>
</div>
  {err&&<div className="error-banner">
<AlertTriangle size={17}/>
<div>
<b>Simulation failed</b>
<span>{
          err}
</span>
</div>
</div>}
<div className="grid-2">
   <section className="panel">
<div className="panel-head">
<h3>
<GitCompareArrows size={
       17}/> Proposed policy</h3>
<span className="muted small">SIMULATION ONLY</span>
</div>
<textarea className="code-editor" value={
       text} onChange={e=>setText(e.target.value)} rows={22}/>
<p className="muted small">No policy is published,
   mutated, or sent to the Data Plane.</p>
</section>
   <section className="panel">
<div className="panel-head">
<h3>
<ShieldAlert size={
       17}/> Impact summary</h3>
</div>{!data?<div className="empty">
<GitCompareArrows size={
           25}/>
<b>Run a simulation</b>
<small>Before/after enforcement impact will appear here.</small>
</div>:<>
    <div className="metric-grid">
<div className="metric">
<div>
<small>Changed decisions</small>
<b>{
        data.changedDecisions}
</b>
</div>
</div>
<div className="metric">
<div>
<small>New DENY</small>
<b>{
        data.newlyDenied}
</b>
</div>
</div>
<div className="metric">
<div>
<small>New STEP_UP</small>
<b>{
        data.newlyStepUp}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Risk reduction</small>
<b>{
        data.estimatedRiskReductionPct}%</b>
</div>
</div>
</div>
    <div className="feature-grid">
<span>
<b>Baseline DENY</b> {data.baselineDenied}
</span>
<span>
<b>Projected DENY</b> {data.projectedDenied}
</span>
<span>
<b>Impact</b> <Pill v={
        data.blastRadiusImpact}/>
</span>
<span>
<b>Resources</b> {(data.impactedResources||[]).length}
</span>
</div>
    <h4>Scenario results</h4>
<table>
<thead>
<tr>
<th>Scenario</th>
<th>Action</th>
<th>Before</th>
<th>After</th>
<th>Changed</th>
</tr>
</thead>
<tbody>{
        (data.results||[]).map((x:any)=>
<tr key={x.id}>
<td>{x.description}
</td>
<td>
<code>{
            x.action}
</code>
</td>
<td>
<Pill v={x.before}/>
</td>
<td>
<Pill v={x.after}
        />
</td>
<td>{x.changed?<CheckCircle2 size={16}/>:''}
</td>
</tr>)}
</tbody>
</table>
   </>}
</section>
  </div>
  <section className="panel">
<div className="panel-head">
<h3>Safe deployment boundary</h3>
</div>
<div className="policy-flow">
<span>Proposed Policy</span>
<i>→</i>
<span>What-if</span>
<i>→</i>
<span>Blast Radius</span>
<i>→</i>
<span>Diff</span>
<i>→</i>
<span className="final">Human Approval</span>
</div>
<div className="feature-grid">
<span>✓ Stateless simulation</span>
<span>✓ Current active policies included</span>
<span>✓ No runtime mutation</span>
<span>✓ Publish still requires existing lifecycle controls</span>
</div>
</section>
 </div>
}

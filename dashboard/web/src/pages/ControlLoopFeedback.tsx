import usePageRefresh from '../components/usePageRefresh';
import React from 'react';
import { RefreshCw, CheckCircle2, AlertTriangle, Activity } from 'lucide-react';
import { apiGet, apiPost } from '../api/client';

export default function ControlLoopFeedback(){
 const [rows,setRows]=React.useState<any[]>([]),[profiles,setProfiles]=React.useState<any[]>([]),
 [err,setErr]=React.useState(''),[busy,setBusy]=React.useState<string | null>(null);
const load=async()=>{try{const [r,p]=await Promise.all([apiGet('/v1/security/control-loop/feedback?observationHours=6'),
         apiGet('/v1/security/control-loop/effectiveness')]);
         setRows(r);
         setProfiles(p);
         setErr('')}catch(e:any){setErr(e.message)}};
 const verify=async(id:string)=>{try{setBusy(id);
         setErr('');
         await apiPost(`/v1/security/control-loop/feedback/${
             id}/verify?observationHours=6`);await load()}catch(e:any){setErr(e.message)}
     finally{setBusy(null)}};
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const effective=rows.filter(x=>x.verification==='EFFECTIVE').length;
 const pending=rows.filter(x=>x.verification==='PENDING_OBSERVATION').length;
 return <div>
<div className="page-header">
<div>
<h2>Control Loop Verification</h2>
<p>Verify → Learn: measure whether an approved preventive control actually reduced observed runtime risk.</p>
</div>
<button onClick={
     load}>
<RefreshCw size={14}/> Refresh</button>
</div>
 {err&&<div className="error-banner">
<AlertTriangle size={17}/>{err}
</div>}
<div className="metric-grid">
<div className="metric">
<small>Executed controls</small>
<b>{
     rows.length}
</b>
</div>
<div className="metric">
<small>Effective</small>
<b>{
     effective}
</b>
</div>
<div className="metric">
<small>Pending observation</small>
<b>{
     pending}
</b>
</div>
<div className="metric">
<small>Learning mode</small>
<b>Evidence</b>
</div>
</div>
 <section className="panel">
<div className="panel-head">
<h3>Control outcomes</h3>
<span className="muted">Not a causal guarantee;
 based on observed runtime evidence</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Action</th>
<th>Target</th>
<th>Before Risk</th>
<th>After Risk</th>
<th>Δ Risk</th>
<th>Outcome</th>
<th>Confidence</th>
<th>Learning</th>
<th>Verify</th>
</tr>
</thead>
<tbody>{
     rows.map(x=>
<tr key={x.responseId}>
<td>
<b>{x.actionType}
</b>
</td>
<td>{
         x.target}
</td>
<td>{x.beforeRisk ?? '—'}
</td>
<td>{x.afterRisk ?? '—'}
</td>
<td>{
         x.riskDelta ?? '—'}
</td>
<td>{x.verification==='EFFECTIVE'?<span>
<CheckCircle2 size={
             14}/> EFFECTIVE</span>:x.verification==='INEFFECTIVE'?<span>
<AlertTriangle size={
             14}/> INEFFECTIVE</span>:<span>
<Activity size={14}/> {x.verification}
</span>}
</td>
<td>{x.confidence ?? '—'}
</td>
<td>{x.learning}
</td>
<td>
<button onClick={
         ()=>verify(x.responseId)} disabled={busy===x.responseId}>{busy===x.responseId?'Recording…':'Verify & record'}
</button>
</td>
</tr>)}
</tbody>
</table>
</div>
</section>
 <section className="panel">
<div className="panel-head">
<h3>Adaptive effectiveness profiles</h3>
<span className="muted">Historical evidence · advisory only</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Action</th>
<th>Target</th>
<th>Score</th>
<th>Confidence</th>
<th>Evidence</th>
<th>Effective</th>
<th>Ineffective</th>
<th>Recommendation</th>
</tr>
</thead>
<tbody>{
     profiles.map(p=>
<tr key={p.profileId}>
<td>
<b>{p.actionType}
</b>
</td>
<td>{
         p.target}
</td>
<td>{p.effectivenessScore}%</td>
<td>{p.confidence}
</td>
<td>{
         p.evidenceCount}
</td>
<td>{p.effectiveCount}
</td>
<td>{p.ineffectiveCount}
</td>
<td>{p.effectivenessScore>=80&&p.confidence>=0.70?'PREFERRED':p.effectivenessScore>=60?'CONSIDER':'REVIEW'}
</td>
</tr>)}
</tbody>
</table>
</div>
</section>
 <section className="panel">
<div className="panel-head">
<h3>Closed-loop boundary</h3>
</div>
<div className="grid-2">
<div>
<h4>Verified automatically</h4>
<ul>
<li>Post-execution runtime evidence</li>
<li>Risk delta</li>
<li>DENY-rate delta</li>
<li>Observation sufficiency</li>
</ul>
</div>
<div>
<h4>Never automated</h4>
<ul>
<li>Changing production policy weights</li>
<li>Auto-isolating another agent</li>
<li>Declaring causality</li>
<li>Publishing a new policy</li>
</ul>
</div>
</div>
</section>
</div>
}

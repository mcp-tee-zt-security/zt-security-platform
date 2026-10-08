import { RISK_THRESHOLDS } from '../config/risk';
import React from 'react';
import { Activity, AlertTriangle, RefreshCw, ShieldCheck, TrendingUp, TrendingDown } from 'lucide-react';
import { apiGet } from '../api/client';
const riskClass=(n:number)=>n >= RISK_THRESHOLDS.critical
    ? 'critical'
    : n >= RISK_THRESHOLDS.high
      ? 'high'
      : n >= RISK_THRESHOLDS.medium
        ? 'medium'
        : 'low';
const time=(x:any)=>x?new Date(x).toLocaleString():'—';
export default function AgentRisk(){
 const [data,setData]=React.useState<any>(null),[agent,setAgent]=React.useState(''),
 [detail,setDetail]=React.useState<any>(null),[err,setErr]=React.useState('');
 const load=async()=>{try{setData(await apiGet('/v1/security/agent-risk/overview?windowHours=24'));
         setErr('')}catch(e:any){setErr(e.message)}};
const loadAgent=async(id:string)=>{setAgent(id);
    try{setDetail(await apiGet('/v1/security/agent-risk/'+encodeURIComponent(id)+'?windowHours=24'))}
     catch(e:any){setErr(e.message)}};
 React.useEffect(()=>{load()},[]);
 return <div>
  <div className="page-header">
<div>
<h2>Continuous Agent Risk</h2>
<p>AI Agent risk as a time-varying security state: score,
  drift, drivers and early warning.</p>
</div>
<button onClick={load}>
<RefreshCw size={
      14}/> Refresh</button>
</div>
  {err&&<div className="error-banner">
<AlertTriangle size={17}/>
<span>{err}
</span>
</div>}
  {data&&<>
   <div className="metric-grid">
<div className="metric">
<div className="metric-icon">
<ShieldCheck/>
</div>
<div>
<small>Average risk</small>
<b>{
       Number(data.averageRisk).toFixed(1)}
</b>
</div>
</div>
<div className="metric">
<div className="metric-icon">
<Activity/>
</div>
<div>
<small>Agents</small>
<b>{
       data.agentCount}
</b>
</div>
</div>
<div className="metric">
<div className="metric-icon">
<AlertTriangle/>
</div>
<div>
<small>High risk</small>
<b>{
       data.highRiskAgents}
</b>
</div>
</div>
<div className="metric">
<div className="metric-icon">
<AlertTriangle/>
</div>
<div>
<small>Critical</small>
<b>{
       data.criticalAgents}
</b>
</div>
</div>
</div>
   <div className="grid-2">
    <section className="panel">
<div className="panel-head">
<h3>Agent risk ranking</h3>
<span className="muted">24h window · {
        data.eventCount} runtime events</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Agent</th>
<th>Risk</th>
<th>State</th>
<th>Drift</th>
<th>Events</th>
<th/>
</tr>
</thead>
<tbody>{
        (data.agents||[]).map((x:any)=>
<tr key={x.agent}>
<td>
<b>{x.agent}
</b>
</td>
<td>
<strong className={
            riskClass(Number(x.riskScore))}>{Number(x.riskScore).toFixed(1)}
</strong>
</td>
<td>{
            x.riskState}
</td>
<td>{x.riskDirection==='RISING'?<TrendingUp size={14}
            />:x.riskDirection==='FALLING'?<TrendingDown size={14}/>: '—'} {Number(x.riskDrift).toFixed(1)}
</td>
<td>{x.eventCount}
</td>
<td>
<button onClick={()=>loadAgent(x.agent)}
        >Inspect</button>
</td>
</tr>)}
</tbody>
</table>
</div>{!data.agents?.length&&<div className="empty">
<Activity size={
            24}/>
<b>No runtime risk data</b>
</div>}
</section>
    <section className="panel">
<div className="panel-head">
<h3>Risk lifecycle</h3>
</div>
<div className="policy-flow">
<span>Runtime</span>
<i>→</i>
<span>Behavior</span>
<i>→</i>
<span>Risk</span>
<i>→</i>
<span>Drift</span>
<i>→</i>
<span className="final">Early Warning</span>
</div>
<p className="muted">Risk is derived from runtime decision pressure,
    event risk and behavioral signals. It does not directly mutate enforcement.</p>
</section>
   </div>
  </>}
  {detail&&<section className="panel">
<div className="panel-head">
<h3>{
          detail.agent} · Risk Timeline</h3>
<span className={`status ${String(detail.riskState).toLowerCase()}
          `}>{detail.riskState} · {detail.riskDirection}
</span>
</div>
<div className="metric-grid">
<div className="metric">
<div>
<small>Current score</small>
<b>{
          detail.riskScore}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Drift</small>
<b>{
          detail.riskDrift}
</b>
</div>
</div>
<div className="metric">
<div>
<small>DENY</small>
<b>{
          detail.denyCount}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Recommended action</small>
<b>{
          detail.recommendedAction}
</b>
</div>
</div>
</div>
<h4>Risk drivers</h4>
<ul>{
          (detail.riskDrivers||[]).map((x:string)=>
<li key={x}>{x}
</li>)}
</ul>
<h4>Timeline</h4>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Time</th>
<th>Risk</th>
<th>Events</th>
<th>DENY</th>
<th>High-risk</th>
</tr>
</thead>
<tbody>{
          (detail.timeline||[]).map((x:any,i:number)=>
<tr key={i}>
<td>{time(x.timestamp)}
</td>
<td>
<strong className={riskClass(Number(x.riskScore))}>{Number(x.riskScore).toFixed(1)}
</strong>
</td>
<td>{x.events}
</td>
<td>{x.deny}
</td>
<td>{x.highRisk}
</td>
</tr>)}
</tbody>
</table>
</div>{detail.earlyWarning&&<div className="notice">
<AlertTriangle size={
              16}/> Early warning: investigate before the risk reaches containment threshold.</div>}
</section>}
</div>
}

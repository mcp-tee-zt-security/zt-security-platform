import usePageRefresh from '../components/usePageRefresh';
import React from 'react';
import { AlertTriangle, RefreshCw, TrendingUp } from 'lucide-react';
import { apiGet } from '../api/client';
import { RISK_THRESHOLDS } from '../config/risk';
const cls=(n:number)=>n>=RISK_THRESHOLDS.critical?'critical':n>=RISK_THRESHOLDS.high?'high':n>=RISK_THRESHOLDS.medium?'medium':'low';
export default function RiskForecast(){
 const [data,setData]=React.useState<any>(null),[selected,setSelected]=React.useState<any>(null),
 [err,setErr]=React.useState('');
const load=async()=>{try{setData(await apiGet('/v1/security/agent-risk/forecast/overview?windowHours=24&horizonHours=12'));
         setErr('')}catch(e:any){setErr(e.message)}};
const inspect=async(id:string)=>{setSelected(null);setErr('');try{setSelected(await apiGet('/v1/security/agent-risk/forecast/'+encodeURIComponent(id)+'?windowHours=24&horizonHours=12'))}
     catch(e:any){setErr(e.message)}};
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 return <div>
<div className="page-header">
<div>
<h2>Agent Risk Forecast</h2>
<p>Explainable forecast from risk drift,
 runtime behavior and exposure signals.</p>
</div>
<button onClick={load}
 >
<RefreshCw size={14}/> Refresh</button>
</div>
 {err&&<div className="error-banner">
<AlertTriangle size={17}/>{err}
</div>}
 {data&&<>
<div className="metric-grid">
<div className="metric">
<div>
<small>Agents</small>
<b>{
         data.agentCount}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Forecast window</small>
<b>{
         data.forecastHours}h</b>
</div>
</div>
<div className="metric">
<div>
<small>High probability</small>
<b>{
         data.highProbabilityAgents}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Events</small>
<b>{
         data.eventCount}
</b>
</div>
</div>
</div>
 <section className="panel">
<div className="panel-head">
<h3>Risk forecast ranking</h3>
<span className="muted">12h deterministic forecast</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Agent</th>
<th>Current</th>
<th>Forecast</th>
<th>Probability</th>
<th>Confidence</th>
<th>Recommendation</th>
<th/>
</tr>
</thead>
<tbody>{
     (data.forecasts||[]).map((x:any)=>
<tr key={x.agent}>
<td>
<b>{x.agent}
</b>
</td>
<td>{
         x.currentScore}
</td>
<td>
<strong className={cls(Number(x.forecastScore))}
     >{x.forecastScore}
</strong> <TrendingUp size={13}/>
</td>
<td>{Math.round(x.highProbability*100)}
     %</td>
<td>{Math.round(x.confidence*100)}%</td>
<td>{x.recommendation}
</td>
<td>
<button onClick={
         ()=>inspect(x.agent)}>Inspect</button>
</td>
</tr>)}
</tbody>
</table>
</div>
</section>
</>}
 {selected&&<section className="panel">
<div className="panel-head">
<h3>{
         selected.agent} · Forecast</h3>
<span className={`status ${String(selected.forecastBand).toLowerCase()}
         `}>{selected.forecastBand}
</span>
</div>
<div className="metric-grid">
<div className="metric">
<div>
<small>Current</small>
<b>{
         selected.currentScore}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Forecast</small>
<b>{
         selected.forecastScore}
</b>
</div>
</div>
<div className="metric">
<div>
<small>High-risk probability</small>
<b>{
         Math.round(selected.highProbability*100)}%</b>
</div>
</div>
<div className="metric">
<div>
<small>Confidence</small>
<b>{
         Math.round(selected.confidence*100)}%</b>
</div>
</div>
</div>
<div className="grid-2">
<div>
<h4>Risk drivers</h4>
<ul>{
         (selected.drivers||[]).map((x:string)=>
<li key={x}>{x}
</li>)}
</ul>
</div>
<div>
<h4>Exposure paths</h4>
<ul>{
         (selected.exposurePaths||[]).map((x:string)=>
<li key={x}>{x}
</li>)}
</ul>
</div>
</div>
<div className="notice">
<AlertTriangle size={
         16}/> {selected.explainability}
</div>
</section>}
</div>
}

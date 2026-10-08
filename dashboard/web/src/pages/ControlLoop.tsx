import React from 'react';
import { AlertTriangle, RefreshCw, ShieldCheck } from 'lucide-react';
import { apiGet, apiPost } from '../api/client';
import { RISK_THRESHOLDS } from '../config/risk';

export default function ControlLoop(){
 const [data,setData]=React.useState<any>(null),[selected,setSelected]=React.useState<any>(null),
 [msg,setMsg]=React.useState(''),[err,setErr]=React.useState('');
const load=async()=>{try{setData(await apiGet('/v1/security/agent-risk/forecast/overview?windowHours=24&horizonHours=12'));
         setErr('')}catch(e:any){setErr(e.message)}};
 React.useEffect(()=>{load()},[]);
 const propose=async(x:any)=>{try{const r=await apiPost('/v1/security/responses/forecast-propose',
         {agent:x.agent,forecastScore:x.forecastScore,highProbability:x.highProbability,
             recommendation:x.recommendation,requestedBy:'dashboard-admin'});
             setSelected(x);
setMsg(r.length?'Preventive control proposed. Approval is still required.':'An equivalent pending control already exists.')}
     catch(e:any){setErr(e.message)}};
 return <div>
<div className="page-header">
<div>
<h2>Preventive Security Control Loop</h2>
<p>Forecast → recommend → human approval → controlled response. No autonomous enforcement.</p>
</div>
<button onClick={
     load}>
<RefreshCw size={14}/> Refresh</button>
</div>
 {err&&<div className="error-banner">
<AlertTriangle size={17}/>{err}
</div>}
 {msg&&<div className="notice">
<ShieldCheck size={16}/>{msg}
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
<small>High probability</small>
<b>{
         data.highProbabilityAgents}
</b>
</div>
</div>
<div className="metric">
<div>
<small>Forecast horizon</small>
<b>{
         data.forecastHours}h</b>
</div>
</div>
<div className="metric">
<div>
<small>Control mode</small>
<b>Approval</b>
</div>
</div>
</div>
 <section className="panel">
<div className="panel-head">
<h3>Preventive candidates</h3>
<span className="muted">No action is executed from this page</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Agent</th>
<th>Forecast</th>
<th>Probability</th>
<th>Recommendation</th>
<th>Control</th>
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
         x.forecastScore}
</td>
<td>{Math.round(x.highProbability*100)}%</td>
<td>{
         x.recommendation}
</td>
<td>{Number(x.forecastScore)>=RISK_THRESHOLDS.critical?'ISOLATE_AGENT':Number(x.forecastScore)>=RISK_THRESHOLDS.high?'STEP_UP_POLICY':'CREATE_DENY_POLICY'}
</td>
<td>
<button onClick={()=>propose(x)}>Propose</button>
</td>
</tr>)}
</tbody>
</table>
</div>
</section>
</>}
<section className="panel">
<div className="panel-head">
<h3>Control boundary</h3>
</div>
<div className="grid-2">
<div>
<h4>Automated</h4>
<ul>
<li>Risk forecast</li>
<li>Threshold classification</li>
<li>Preventive recommendation</li>
<li>Evidence/audit event</li>
</ul>
</div>
<div>
<h4>Human controlled</h4>
<ul>
<li>Approve response</li>
<li>Execute isolation/revocation</li>
<li>Publish policy</li>
<li>Credential rotation</li>
</ul>
</div>
</div>
</section>
</div>
}

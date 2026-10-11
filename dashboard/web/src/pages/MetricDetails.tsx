import React from 'react';
import {apiGet} from '../api/client';
import {MetricSelection,metricRows} from '../components/MetricLink';
import {RISK_THRESHOLDS} from '../config/risk';
import usePageRefresh from '../components/usePageRefresh';
function filtered(rows:any[],label:string){
 if(['Denied','DENY','New DENY'].includes(label))return rows.filter(r=>r.decision==='DENY'||r.after==='DENY');
 if(['Step-up','STEP_UP','New STEP_UP'].includes(label))return rows.filter(r=>r.decision==='STEP_UP'||r.after==='STEP_UP');
 if(label==='ALLOW')return rows.filter(r=>r.decision==='ALLOW');
 if(label==='High risk')return rows.filter(r=>Number(r.riskScore??r.compositeScore)>=RISK_THRESHOLDS.high);
 if(['Critical','Critical risk','Critical graph nodes','Critical assets'].includes(label))return rows.filter(r=>r.severity==='CRITICAL'||r.riskState==='CRITICAL'||Number(r.riskScore??r.compositeScore??r.score)>=RISK_THRESHOLDS.critical||Number(r.criticality)>=RISK_THRESHOLDS.critical);
 if(label==='Changed decisions'||label==='Changed')return rows.filter(r=>r.changed);
 if(label==='Restricted / Confidential')return rows.filter(r=>['RESTRICTED','CONFIDENTIAL'].includes(r.classification));
 if(label==='Active policies')return rows.filter(r=>r.status==='ACTIVE');
 if(label==='High probability')return rows.filter(r=>Number(r.highProbability)>=.6);
 if(label==='Awaiting approval')return rows.filter(r=>r.status==='PENDING');
 if(label==='Approved')return rows.filter(r=>r.status==='APPROVED');
 if(label==='Executed')return rows.filter(r=>r.status==='EXECUTED');
 if(label==='Open cases'||label==='Open cases in window')return rows.filter(r=>['OPEN','INVESTIGATING','CONTAINED'].includes(r.status));
 if(label==='SOAR handoffs')return rows.filter(r=>r.externalRef&&r.externalRef!=='null');
 if(label==='Effective')return rows.filter(r=>r.verification==='EFFECTIVE');
 if(label==='Pending observation')return rows.filter(r=>r.verification==='PENDING_OBSERVATION');
 if(label==='MCP gateways')return rows.filter(r=>r.type==='MCP_GATEWAY');
 return rows;
}
async function recordsFor(selection:MetricSelection){
 const {sourcePage:page,label,subject}=selection;
 if(selection.records!==undefined)return {rows:filtered(selection.records,label),scope:'SOURCE_SNAPSHOT'};
 if(page==='Runtime Intelligence'){
  const view=label==='Active sessions'?'SESSIONS':label==='Observed agents'?'AGENTS':'ANOMALIES';
  const response=await apiGet('/v1/runtime/intelligence/records?'+new URLSearchParams({view,severity:label==='High / critical'?'HIGH_CRITICAL':'ALL'}));return {rows:response.items,scope:response.scope};
 }
 let path='';let key='';
 if(label==='Critical graph nodes'){path='/v1/security-graph?windowMinutes=120';key='nodes';}
 else if(label==='Open cases in window')path='/v1/security/cases';
 else if(label==='Active policies')path='/v1/policies/all';
 else if(label==='Risk score')path='/v1/security/decisions';
 else if(['Overview','Decision Engine','Command Center'].includes(page))path='/v1/security/decisions';
 else if(page==='Enterprise')path=label==='Directory users'?'/v1/enterprise/directory/users':label==='Enterprise roles'?'/v1/rbac/roles':label==='SSO connections'?'/v1/enterprise/sso':'/v1/rbac/me/permissions';
 else if(page==='Continuous Agent Risk'){path='/v1/security/agent-risk/overview?windowHours=24';key='agents';}
 else if(['Agent Risk Forecast','Preventive Control Loop'].includes(page)){path='/v1/security/agent-risk/forecast/overview?windowHours=24&horizonHours=12';key='forecasts';}
 else if(page==='Control Loop Verification')path='/v1/security/control-loop/feedback?observationHours=6';
 else if(page==='Incident Response')path='/v1/security/cases';
 else if(page==='Response Center')path='/v1/security/responses';
 else if(page==='Agent Behavior')path=label==='Profiles'?'/v1/agents/behavior/profiles':subject?'/v1/agents/behavior/'+encodeURIComponent(subject)+(label==='Anomalies'?'/anomalies':'/decisions'):'/v1/agents/behavior/profiles';
 else if(page==='Security Graph'){path='/v1/security-graph?windowMinutes=120';key=label==='Edges'?'edges':'nodes';}
 else if(label==='Bundle policies'||label==='Active policies')path='/v1/policies/all';
 if(!path)return {rows:[],scope:'UNAVAILABLE'};
 const result=await apiGet(path);const rows=key?result[key]||[]:Array.isArray(result)?result:[result];return {rows:filtered(metricRows(rows),label),scope:'LIVE_RECORDS'};
}
export default function MetricDetails({selection,onBack,onAgent}:{selection:MetricSelection;onBack:()=>void;onAgent:(subject:string)=>void}){
 const [rows,setRows]=React.useState<any[]>([]),[scope,setScope]=React.useState(''),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(true),[selected,setSelected]=React.useState<any>(null);
 const ticket=React.useRef(0);
 const load=React.useCallback(async()=>{const current=++ticket.current;setLoading(true);try{const result=await recordsFor(selection);if(current===ticket.current){setRows(metricRows(result.rows));setScope(result.scope);setError('')}}catch(e:any){if(current===ticket.current)setError(e.message)}finally{if(current===ticket.current)setLoading(false)}},[selection]);
 React.useEffect(()=>{setSelected(null);void load();return()=>{ticket.current++}},[load]);usePageRefresh(load);
 const columns=Array.from(new Set(rows.flatMap(row=>Object.keys(row)))).slice(0,10);
 return <div><div className="page-header"><div><h2>{selection.label} — details</h2><p>Opened from {selection.sourcePage}. {scope==='TENANT'?'Shared tenant runtime inventory.':scope==='SOURCE_SNAPSHOT'?'Read-only snapshot from the source view; no action is executed.':'Records use their existing server authorization.'}</p></div><button onClick={onBack}>Back to {selection.sourcePage}</button></div>{scope!=='SOURCE_SNAPSHOT'&&<button disabled={loading} onClick={()=>void load()}>Refresh details</button>}{error&&<div className="error-banner" role="alert">{error}</div>}{loading&&<p role="status">Loading details…</p>}<section className="panel table-scroll"><p>{rows.length} displayed records {rows.length===500&&'· bounded to 500'}</p><table><thead><tr>{columns.map(c=><th key={c}>{c}</th>)}<th>Inspect</th></tr></thead><tbody>{rows.map((row,i)=><tr key={row.id||row.agent||i}>{columns.map(c=><td key={c}>{row[c]==null?'—':String(row[c])}</td>)}<td><button onClick={()=>setSelected(row)}>Inspect record</button>{(row.agent||row.agentExternalId)&&<button onClick={()=>onAgent(row.agent||row.agentExternalId)}>Open agent</button>}</td></tr>)}</tbody></table>{!loading&&!rows.length&&<p>{scope==='UNAVAILABLE'?'This metric needs the source selection. Return to the source page and select a record.':'No matching records in this view.'}</p>}</section>{selected&&<section className="panel"><h3>Selected record</h3><pre className="json">{JSON.stringify(selected,null,2)}</pre><button onClick={()=>setSelected(null)}>Close record</button></section>}</div>;
}

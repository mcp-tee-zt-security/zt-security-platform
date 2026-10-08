import React from 'react';
import {apiGet} from '../api/client';
import {can,DashboardRole} from '../rbac/permissions';

export default function AgentDetails({subject='',role,onNavigate}:{subject?:string;role:DashboardRole;onNavigate:(page:string,subject?:string)=>void}){
 const [input,setInput]=React.useState(subject||'payment-agent'),[selected,setSelected]=React.useState(subject||'payment-agent');
 const [identities,setIdentities]=React.useState<any[]>([]),[graph,setGraph]=React.useState<any>(null),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(false);
 React.useEffect(()=>{if(subject){setInput(subject);setSelected(subject)}},[subject]);
 React.useEffect(()=>{
   let current=true;setLoading(true);setError('');setGraph(null);setIdentities([]);
   Promise.all([apiGet('/v1/identities'),apiGet(`/v1/agents/${encodeURIComponent(selected)}/permission-graph`)])
    .then(([all,permissions])=>{if(current){setIdentities(all);setGraph(permissions)}})
    .catch(e=>{if(current)setError(e.message)}).finally(()=>{if(current)setLoading(false)});
   return()=>{current=false};
 },[selected]);
 const identity=identities.find(row=>row.externalId===selected&&row.identityType==='AI_AGENT');
 const links=[['Approvals','Approval requests'],['Execution Records','Execution records'],['Audit Logs','Audit records'],['Agent Behavior','Behavior analysis'],['Policy Studio','Policy Studio']] as const;
 return <div>
  <div className="page-header"><div><h2>Agent Details</h2><p>Inspect an agent, then follow its approvals, executions and audit records.</p></div></div>
  <form className="workflow-filter" onSubmit={event=>{event.preventDefault();if(input.trim())setSelected(input.trim())}}><label>Agent external ID<input value={input} onChange={event=>setInput(event.target.value)}/></label><button disabled={!input.trim()||loading}>Load agent</button></form>
  {loading&&<p role="status">Loading agent…</p>}{error&&<div className="error-banner" role="alert">Agent details could not be loaded: {error}</div>}
  <div className="grid-2"><section className="panel"><h3>{selected}</h3>{identity?<dl className="workflow-facts"><dt>Name</dt><dd>{identity.name}</dd><dt>Status</dt><dd>{identity.status}</dd><dt>Identity ID</dt><dd className="mono">{identity.id}</dd></dl>:!loading&&!error&&<p>No matching AI-agent identity is available.</p>}
   <div className="workflow-actions">{links.filter(([page])=>can(role,page)).map(([page,label])=><button key={page} onClick={()=>onNavigate(page,selected)}>{label}</button>)}</div>
   <p className="muted small">Approval and execution links use this agent's external ID. Policy Studio shows tenant policies; it does not imply that all policies are assigned to this agent.</p>
  </section><section className="panel"><h3>Permission graph</h3>{graph?<pre className="json">{JSON.stringify(graph,null,2)}</pre>:<p>No permission graph loaded.</p>}</section></div>
 </div>;
}

import React from 'react';
import AgentConnections from '../components/AgentConnections';
import usePageRefresh from '../components/usePageRefresh';
import SubjectSelect from '../components/SubjectSelect';
import {apiGet} from '../api/client';
import {can,DashboardRole} from '../rbac/permissions';

export default function AgentDetails({subject='',role,onNavigate}:{subject?:string;role:DashboardRole;onNavigate:(page:string,subject?:string)=>void}){
 const [input,setInput]=React.useState(subject||''),[selected,setSelected]=React.useState(subject||'');
 const [revision,setRevision]=React.useState(0);usePageRefresh(()=>setRevision(x=>x+1));
 const [identities,setIdentities]=React.useState<any[]>([]),[graph,setGraph]=React.useState<any>(null),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(false);
 React.useEffect(()=>{if(subject){setInput(subject);setSelected(subject)}},[subject]);
 React.useEffect(()=>{
   let current=true;setLoading(true);setError('');setGraph(null);setIdentities([]);
   Promise.all([apiGet('/v1/identities'),selected?apiGet(`/v1/agents/${encodeURIComponent(selected)}/permission-graph`):Promise.resolve(null)])
    .then(([all,permissions])=>{if(current){setIdentities(all);setGraph(permissions)}})
    .catch(e=>{if(current)setError(e.message)}).finally(()=>{if(current)setLoading(false)});
   return()=>{current=false};
 },[selected,revision]);
 const [calls,setCalls]=React.useState<any[]>([]),[callError,setCallError]=React.useState('');
 React.useEffect(()=>{let current=true;setCalls([]);setCallError('');if(selected)apiGet('/v1/mcp/agent-calls?subject='+encodeURIComponent(selected)).then(x=>{if(current)setCalls(x)}).catch(e=>{if(current)setCallError(e.message)});return()=>{current=false}},[selected,revision]);
 const identity=identities.find(row=>row.externalId===selected&&row.identityType==='AI_AGENT');
 const links=[['Approvals','Approval requests'],['Execution Records','Execution records'],['Audit Logs','Audit records'],['Agent Behavior','Behavior analysis'],['Policy Studio','Policy Studio']] as const;
 return <div>
  <div className="page-header"><div><h2>Agent Details</h2><p>Inspect an agent, then follow its approvals, executions and audit records.</p></div></div>
  <form className="workflow-filter" onSubmit={event=>{event.preventDefault();if(input.trim())setSelected(input.trim())}}><label>Agent external ID<SubjectSelect value={input} onChange={v=>{setInput(v);setSelected(v);setGraph(null);setError('')}}/></label><button disabled={!input.trim()||loading}>Load agent</button></form>
  {loading&&<p role="status">Loading agent…</p>}{error&&<div className="error-banner" role="alert">Agent details could not be loaded: {error}</div>}
  <div className="grid-2"><section className="panel"><h3>{selected||'Select an agent to inspect'}</h3>{identity?<dl className="workflow-facts"><dt>Name</dt><dd>{identity.name}</dd><dt>Status</dt><dd>{identity.status}</dd><dt>Identity ID</dt><dd className="mono">{identity.id}</dd></dl>:!!selected&&!loading&&!error&&<p>No matching AI-agent identity is available.</p>}
   <div className="workflow-actions">{links.filter(([page])=>!!selected&&can(role,page)).map(([page,label])=><button key={page} onClick={()=>onNavigate(page,selected)}>{label}</button>)}</div>
   <p className="muted small">Approval and execution links use this agent's external ID. Policy Studio shows tenant policies; it does not imply that all policies are assigned to this agent.</p>
  </section><section className="panel"><h3>Permission graph</h3>{graph?<pre className="json">{JSON.stringify(graph,null,2)}</pre>:<p>No permission graph loaded.</p>}</section></div>
 {selected&&<><AgentConnections agentExternalId={selected}/><section className="panel"><h3>Agent MCP approvals & execution</h3><p>Latest 100 calls for this Agent in the current workspace. Service authentication and policy Agent are recorded separately.</p>{callError&&<div className="error-banner" role="alert">{callError}</div>}<div className="table-scroll"><table><thead><tr><th>Service</th><th>Agent / tool</th><th>Execution</th><th>Approval</th><th>Call ID</th></tr></thead><tbody>{calls.map(x=><tr key={x.callId}><td>{x.requestedBy}</td><td>{x.policySubject}<br/>{x.toolName}</td><td>{x.status}</td><td>{x.approvalStatus||'Not required'}{x.approvalId&&<small className="mcp-block">{x.approvalId}</small>}</td><td><code>{x.callId}</code></td></tr>)}</tbody></table></div>{!callError&&!calls.length&&<p>No MCP calls for this Agent.</p>}{can(role,'MCP Gateway')&&<button onClick={()=>onNavigate('MCP Gateway',selected)}>Review MCP approvals and calls</button>}</section></>}
 </div>;
}

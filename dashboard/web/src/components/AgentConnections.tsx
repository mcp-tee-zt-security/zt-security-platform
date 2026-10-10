import React from 'react';
import {apiGet,apiPost,WORKSPACE} from '../api/client';

export default function AgentConnections({agentExternalId=''}:{agentExternalId?:string}) {
 const [bindings,setBindings]=React.useState<any[]>([]),[clients,setClients]=React.useState<any[]>([]),[agents,setAgents]=React.useState<any[]>([]);
 const [canManage,setCanManage]=React.useState(false),[error,setError]=React.useState(''),[message,setMessage]=React.useState(''),[busy,setBusy]=React.useState(false);
 const [client,setClient]=React.useState(''),[agent,setAgent]=React.useState(''),[isDefault,setDefault]=React.useState(false);
 const ticket=React.useRef(0);
 const load=React.useCallback(async()=>{
  const current=++ticket.current;setBusy(true);setError('');
  try {
   const context=await apiGet('/v1/mcp/management/context');
   const [rows,identities,accounts]=await Promise.all([apiGet('/v1/mcp/agent-bindings'),apiGet('/v1/identities'),context.canManage?apiGet('/v1/clients'):Promise.resolve([])]);
   if(current!==ticket.current)return;
   setBindings(rows);setAgents(identities.filter((x:any)=>x.identityType==='AI_AGENT'&&x.status==='ACTIVE'));setClients(accounts.filter((x:any)=>x.status==='ACTIVE'&&(!x.workspaceId||x.workspaceId===WORKSPACE)));setCanManage(context.canManage);
  }catch(e:any){if(current===ticket.current){setError(e.message);setCanManage(false)}}finally{if(current===ticket.current)setBusy(false)}
 },[]);
 React.useEffect(()=>{void load();window.addEventListener('zt-refresh',load);return()=>{ticket.current++;window.removeEventListener('zt-refresh',load)}},[load]);
 React.useEffect(()=>{if(agentExternalId)setAgent(agents.find(x=>x.externalId===agentExternalId)?.id||'')},[agents,agentExternalId]);
 const save=async(clientId:string,agentId:string,enabled:boolean,makeDefault:boolean)=>{
  setBusy(true);setError('');setMessage('');
  try{await apiPost('/v1/mcp/agent-bindings',{clientId,agentId,enabled,isDefault:enabled&&makeDefault});await load();window.dispatchEvent(new Event('zt-refresh'));setMessage(enabled?'Agent connection saved. New calls use the selected Agent policy; existing approvals cannot be reused after this connection changes.':'Connection disabled. Calls for this Agent are blocked.')}catch(e:any){setError(e.message)}finally{setBusy(false)}
 };
 const visible=bindings.filter(x=>!agentExternalId||x.agentExternalId===agentExternalId);
 return <section className="panel agent-connections"><h3>Service ↔ Agent connections</h3>
  <p>One service can use multiple Agents. Each request selects a linked Agent with <code>X-ZT-Agent-Id</code>. A default Agent is used when the header is omitted; a single enabled connection is also selected automatically.</p>
  <p className="muted">Without any connections, existing services keep their service subject. Disabling connections never silently restores that legacy access. MCP tool permissions still apply to the service account.</p>
  {error&&<div className="error-banner" role="alert">{error}</div>}{message&&<p role="status">{message}</p>}
  {canManage&&<form className="workflow-filter" onSubmit={e=>{e.preventDefault();void save(client,agent,true,isDefault)}}>
   <label>Service account<select value={client} disabled={busy} onChange={e=>setClient(e.target.value)}><option value="">Select a service</option>{clients.map(x=><option key={x.id} value={x.id}>{x.name} · client:{x.clientId}</option>)}</select></label>
   <label>AI Agent<select value={agent} disabled={busy||!!agentExternalId} onChange={e=>setAgent(e.target.value)}><option value="">Select an Agent</option>{agents.map(x=><option key={x.id} value={x.id}>{x.name} · {x.externalId}</option>)}</select></label>
   <label className="agent-check"><input type="checkbox" checked={isDefault} disabled={busy} onChange={e=>setDefault(e.target.checked)}/> Default Agent</label>
   <button disabled={busy||!client||!agent}>Connect Agent</button>
  </form>}
  <div className="table-scroll"><table><thead><tr><th>Authenticated service</th><th>Policy Agent</th><th>Connection</th><th>Default</th>{canManage&&<th>Manage</th>}</tr></thead><tbody>{visible.map(x=><tr key={x.id}><td>client:{x.clientExternalId}</td><td>{x.agentName}<br/><code>{x.agentExternalId}</code></td><td>{x.enabled?'Enabled':'Disabled'}</td><td>{x.isDefault?'Yes':'No'}</td>{canManage&&<td><div className="button-row"><button disabled={busy} onClick={()=>save(x.clientId,x.agentId,!x.enabled,false)}>{x.enabled?'Disable':'Enable'}</button>{x.enabled&&<button disabled={busy} onClick={()=>save(x.clientId,x.agentId,true,!x.isDefault)}>{x.isDefault?'Clear default':'Set default'}</button>}</div></td>}</tr>)}</tbody></table></div>
  {!busy&&!error&&!visible.length&&<p>No service connections for {agentExternalId||'this workspace'}.</p>}
  <button disabled={busy} onClick={()=>load()}>Refresh connections</button>
 </section>;
}

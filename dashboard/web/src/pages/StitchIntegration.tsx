import React from 'react';
import {mcpRequest,McpCredentials} from '../api/client';

const operations:Record<string,{label:string;method:string;path:string}>={
 sourceState:{label:"Read source sync state (connector)",method:"GET",path:"/source-state"},
 checkpoint:{label:"Commit source sync checkpoint (connector)",method:"POST",path:"/source-state"},
 context:{label:'Verify authenticated identity',method:'GET',path:'/context'},
 subjects:{label:'Sync user/group membership (connector)',method:'POST',path:'/subjects'},
 resources:{label:'Sync resource + ACL + chunks (connector)',method:'POST',path:'/resources'},
 delegate:{label:'Create AI delegation (human)',method:'POST',path:'/sessions'},
 retrieve:{label:'Read authorized resource',method:'POST',path:'/retrieve'},
 children:{label:'List authorized children/messages',method:'POST',path:'/children'},
 search:{label:'Search / RAG retrieval',method:'POST',path:'/search'},
 mcp:{label:'MCP JSON-RPC adapter',method:'POST',path:'/mcp'},
 calls:{label:'Own retrieval audit history',method:'GET',path:'/calls'},
 events:{label:'Pending external deletion events',method:'GET',path:'/deletion-events'},
 retention:{label:'Set retention ceiling (connector)',method:'PUT',path:'/retention'},
 maintenance:{label:'Run retention maintenance (connector)',method:'POST',path:'/maintenance'},
 revokeToken:{label:'Revoke source JWT (connector)',method:'POST',path:'/revoked-tokens'},
 revokeSession:{label:'Revoke delegation',method:'DELETE',path:'/sessions/'},
 deleteResource:{label:'Delete resource subtree (connector)',method:'DELETE',path:'/resources/'},
 ack:{label:'Acknowledge recipient deletion',method:'POST',path:'/deletion-events/'},
};
export default function StitchIntegration(){
 const [draft,setDraft]=React.useState<McpCredentials>({mode:'bearer'}),[auth,setAuth]=React.useState<McpCredentials|null>(null),[identity,setIdentity]=React.useState<any>(null),[operation,setOperation]=React.useState(''),[body,setBody]=React.useState('{}'),[id,setId]=React.useState(''),[session,setSession]=React.useState(''),[result,setResult]=React.useState<any>(null),[error,setError]=React.useState(''),[busy,setBusy]=React.useState(false);
 const apply=async()=>{setBusy(true);setError('');setResult(null);setIdentity(null);setAuth(null);try{const c={...draft};const r=await mcpRequest('/v1/integrations/stitch/context','GET',undefined,c);setAuth(c);setIdentity(r);setDraft({...draft,secret:''});}catch(e:any){setError(e.message)}finally{setBusy(false)}};
 const execute=async()=>{if(!auth||!operation)return;setBusy(true);setError('');setResult(null);try{const op=operations[operation];const suffix=operation==='ack'?encodeURIComponent(id)+'/ack':['revokeSession','deleteResource'].includes(operation)?encodeURIComponent(id):'';const headers:Record<string,string>={};if(operation==='mcp'){headers['MCP-Protocol-Version']='2025-06-18';if(session)headers['X-ZT-Delegation']=session;}const r=await mcpRequest('/v1/integrations/stitch'+op.path+suffix,op.method,['GET','DELETE'].includes(op.method)?undefined:JSON.parse(body),auth,headers);setResult(r);if(r?.sessionId)setSession(r.sessionId);}catch(e:any){setError(e.message)}finally{setBusy(false)}};
 return <div><h2>Stitch Integration Console</h2><p>Standard integration contract, separate from synthetic PoC. Requires source ACL sync and configured OIDC. No actual Stitch system is connected automatically.</p>{error&&<div className="error-banner" role="alert">{error}</div>}
 <section className="panel"><h3>Authenticated actor</h3><label>Credential<select disabled={busy} value={draft.mode} onChange={e=>{setDraft({mode:e.target.value as McpCredentials['mode']});setAuth(null);setIdentity(null);setResult(null)}}><option value="bearer">OIDC JWT — human / AI / connector</option><option value="service">Registered AI service client</option></select></label>{draft.mode==='service'&&<label>Client ID<input disabled={busy} value={draft.clientId||''} onChange={e=>setDraft({...draft,clientId:e.target.value})}/></label>}<label>{draft.mode==='bearer'?'Bearer token':'Client secret'}<input type="password" disabled={busy} autoComplete="off" value={draft.secret||''} onChange={e=>setDraft({...draft,secret:e.target.value})}/></label><button disabled={busy||!draft.secret?.trim()||draft.mode==='service'&&!draft.clientId?.trim()} onClick={apply}>Apply and verify</button>{identity&&<p>{identity.subject} · {identity.actorType} · Connector: {String(identity.connector)}</p>}<p>Credentials remain in page memory. The actor comes from verified authentication, not JSON fields.</p></section>
 <div className="grid-2"><section className="panel"><h3>Contract request</h3><label>Operation<select disabled={busy} value={operation} onChange={e=>{setOperation(e.target.value);setResult(null);setBody('{}');setId('')}}><option value="">Select operation</option>{Object.entries(operations).map(([key,op])=><option key={key} value={key}>{op.label}</option>)}</select></label>{['revokeSession','deleteResource','ack'].includes(operation)&&<label>Resource / session / event ID<input disabled={busy} value={id} onChange={e=>{setId(e.target.value);setResult(null)}}/></label>}{operation==='mcp'&&<label>Trusted delegation header (outside tool arguments)<input disabled={busy} value={session} onChange={e=>{setSession(e.target.value);setResult(null)}}/></label>}{operation&&operations[operation].method!=='GET'&&operations[operation].method!=='DELETE'&&<label>Request JSON<textarea disabled={busy} rows={15} value={body} onChange={e=>{setBody(e.target.value);setResult(null)}}/></label>}<button className="primary" disabled={busy||!auth||!operation||(['revokeSession','deleteResource','ack'].includes(operation)&&!id)} onClick={execute}>{busy?'Sending…':'Send contract request'}</button><p>Use the documented request schemas. Sync/delete operations require connector credentials. HUMAN requests require a real OIDC token and provisioned source membership.</p></section><section className="panel"><h3>Response</h3>{result?<pre className="json">{JSON.stringify(result,null,2)}</pre>:<p>No request sent.</p>}<p>ACKNOWLEDGED is a recipient statement; it does not establish physical erasure of third-party backups or earlier outputs.</p></section></div>
 </div>;
}

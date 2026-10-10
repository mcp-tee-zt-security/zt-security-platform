import React from 'react';
import {mcpRequest} from '../api/client';
import type {McpCredentials} from '../api/client';

type ServerForm={serverId:string;displayName:string;endpoint:string;bearerTokenEnv:string;enabled:boolean;developmentHttp:boolean};
type ToolForm={toolId:string;name:string;description:string;riskLevel:string;serverId:string;upstreamTool:string;subjects:string;schema:string;paths:string;literals:string;requireApproval:boolean};
const schema=JSON.stringify({type:'object',properties:{orderId:{type:'string',enum:['TEST-ZT-001']}},required:['orderId'],additionalProperties:false},null,2);
const newServer=():ServerForm=>({serverId:'orders',displayName:'Order MCP Server',endpoint:'http://host.docker.internal:9998/mcp',bearerTokenEnv:'',enabled:true,developmentHttp:false});
const newTool=():ToolForm=>({toolId:'',name:'cancelOrder',description:'Cancel a pending test order',riskLevel:'MEDIUM',serverId:'orders',upstreamTool:'cancelOrder',subjects:'api-key',schema,paths:'',literals:'',requireApproval:false});
const lines=(value:string)=>value.split('\n').map(x=>x.trim()).filter(Boolean);
const stamp=(value:any)=>value?new Date(value).toLocaleString():'—';
const Status=({value}:{value:any})=><span className={`status ${String(value||'unknown').toLowerCase()}`}>{value||'UNKNOWN'}</span>;
const Field=({label,children}:{label:string;children:React.ReactNode})=><label className="mcp-field"><span>{label}</span>{children}</label>;
const Panel=({title,children}:{title:string;children:React.ReactNode})=><section className="panel"><h3>{title}</h3>{children}</section>;

export default function McpGateway(){
 const [auth,setAuth]=React.useState<McpCredentials>({mode:'default'}),[credentialDraft,setCredentialDraft]=React.useState<McpCredentials>({mode:'default'});
 const [context,setContext]=React.useState<any>(null),[tab,setTab]=React.useState('execute');
 const [tools,setTools]=React.useState<any[]>([]),[servers,setServers]=React.useState<any[]>([]),[catalog,setCatalog]=React.useState<any[]>([]),[bindings,setBindings]=React.useState<any[]>([]);
 const [policy,setPolicy]=React.useState<any>({}),[history,setHistory]=React.useState<any[]>([]),[approvals,setApprovals]=React.useState<any[]>([]);
 const [serverForm,setServerForm]=React.useState(newServer),[toolForm,setToolForm]=React.useState(newTool),[discovered,setDiscovered]=React.useState<any[]>([]),[discoveredServer,setDiscoveredServer]=React.useState('');
 const [selected,setSelected]=React.useState(''),[argumentsText,setArgumentsText]=React.useState('{\n  "orderId": "TEST-ZT-001"\n}');
 const [result,setResult]=React.useState<any>(null),[lastRequest,setLastRequest]=React.useState<any>(null),[callId,setCallId]=React.useState('');
 const [approvalDetail,setApprovalDetail]=React.useState<any>(null),[busy,setBusy]=React.useState(false),[error,setError]=React.useState(''),[message,setMessage]=React.useState('');
 const ticket=React.useRef(0);
 const request=(path:string,method='GET',body?:unknown)=>mcpRequest(path,method,body,auth);
 const load=async(credentials=auth)=>{
   const current=++ticket.current;setBusy(true);setError('');
   try{
     const c=await mcpRequest('/v1/mcp/management/context','GET',undefined,credentials);
     const [t,h,s,m,a]=await Promise.all([
       mcpRequest('/v1/mcp/tools','GET',undefined,credentials),mcpRequest('/v1/mcp/calls','GET',undefined,credentials),
       c.canManage?mcpRequest('/v1/mcp/servers','GET',undefined,credentials):Promise.resolve({servers:[],registrationPolicy:{}}),
       c.canManage?mcpRequest('/v1/mcp/management/tools','GET',undefined,credentials):Promise.resolve({tools:[],bindings:[]}),
       c.canApprove?mcpRequest('/v1/mcp/approvals','GET',undefined,credentials):Promise.resolve([])
     ]);
     if(current!==ticket.current)return;
     setContext(c);setTools(t);setHistory(h);setServers(s.servers);setPolicy(s.registrationPolicy);
     setCatalog(m.tools);setBindings(m.bindings);setApprovals(a);
     setSelected(value=>t.some((x:any)=>x.toolId===value)?value:t[0]?.toolId||'');
     setDiscoveredServer(value=>s.servers.some((x:any)=>x.serverId===value&&x.enabled)?value:s.servers.find((x:any)=>x.enabled)?.serverId||'');
     if(!c.canManage)setTab(value=>value==='servers'||value==='tools'?'execute':value);
   }catch(e:any){if(current===ticket.current){setContext(null);setError(e.message)}}
   finally{if(current===ticket.current)setBusy(false)}
 };
 React.useEffect(()=>{setContext(null);setTools([]);setServers([]);setCatalog([]);setBindings([]);setApprovals([]);setHistory([]);setDiscovered([]);setApprovalDetail(null);setResult(null);setLastRequest(null);setMessage('');void load(auth);return()=>{ticket.current++}},[auth]);
 const act=async(job:()=>Promise<void>)=>{if(busy)return;setBusy(true);setError('');setMessage('');try{await job()}catch(e:any){setError(e.message)}finally{setBusy(false)}};
 const saveServer=()=>act(async()=>{
   const {serverId,...value}=serverForm;
   await request('/v1/mcp/servers/'+encodeURIComponent(serverId),'PUT',{...value,bearerTokenEnv:value.bearerTokenEnv.trim()||null});
   setDiscovered([]);await load();setMessage('Upstream saved. Load its tools to continue registration.');
 });
 const discover=()=>act(async()=>{
   const x=await request('/v1/mcp/servers/'+encodeURIComponent(discoveredServer)+'/discover','POST',{});
   setDiscovered(x.tools);setMessage(`Loaded ${x.tools.length} upstream tool definitions. No business tool was executed.${x.hasMore?' More tools exist beyond this first page.':''}`);
 });
 const importTool=(row:any)=>{
   const existing=catalog.find(x=>x.name===row.name);
   const imported={...row.inputSchema};delete imported.$schema;
   if(imported.type==='object')imported.additionalProperties=false;
   setToolForm({...newTool(),toolId:existing?.toolId||'',name:row.name,description:row.description||'',riskLevel:existing?.riskLevel||'MEDIUM',
     serverId:discoveredServer,upstreamTool:row.name,subjects:context?.subject||'',schema:JSON.stringify(imported,null,2)});
   setTab('tools');setMessage('Review permissions and schema before saving. Registration does not create an ALLOW policy.');
 };
 const editBinding=(row:any)=>{
   const tool=catalog.find(x=>x.toolId===row.toolId);const c=row.config;
   setToolForm({toolId:row.toolId,name:tool?.name||'',description:tool?.description||'',riskLevel:tool?.riskLevel||'MEDIUM',
     serverId:c.serverId,upstreamTool:c.upstreamTool,subjects:c.allowedSubjects.join('\n'),schema:JSON.stringify(c.inputSchema,null,2),
     paths:c.redactResultPaths.join('\n'),literals:c.redactTextLiterals.join('\n'),requireApproval:c.requireApproval});setTab('tools');
 };
 const saveTool=()=>act(async()=>{
   const inputSchema=JSON.parse(toolForm.schema);
   const x=await request('/v1/mcp/management/tools','POST',{toolId:toolForm.toolId||null,name:toolForm.name,description:toolForm.description,riskLevel:toolForm.riskLevel,
     config:{serverId:toolForm.serverId,upstreamTool:toolForm.upstreamTool,allowedSubjects:lines(toolForm.subjects),inputSchema,
       redactResultPaths:lines(toolForm.paths),redactTextLiterals:lines(toolForm.literals),requireApproval:toolForm.requireApproval}});
   setToolForm({...toolForm,toolId:x.toolId});await load();setSelected(x.toolId);
   setMessage('Tool binding saved. Activate its authorization policy in Policy Studio before execution.');
 });
 const args=()=>{const x=JSON.parse(argumentsText);if(!x||typeof x!=='object'||Array.isArray(x))throw new Error('Arguments must be a JSON object');return x};
 const receive=(x:any)=>{
   setResult(x);const security=x.result?._meta?.['zt.security']||x._meta?.['zt.security']||x;
   if(x.error)setError(`${x.error.message||'MCP request failed'} (RPC ${x.error.code}). Check call history before retrying.`);
   setCallId(security.status==='PENDING_APPROVAL'?security.callId:'');
 };
 const execute=(preview:boolean)=>act(async()=>{
   const tool=tools.find(x=>x.toolId===selected);if(!tool)throw new Error('Select an executable tool');
   let response;
   if(preview)response=await request('/v1/mcp/authorize','POST',{toolId:tool.toolId,arguments:args()});
   else{const body={jsonrpc:'2.0',id:crypto.randomUUID(),method:'tools/call',params:{name:tool.name,arguments:args()}};setLastRequest(body);response=await request('/v1/mcp/json-rpc','POST',body)}
   await load();receive(response);
 });
 const resume=()=>act(async()=>{if(!callId.trim())throw new Error('Call ID is required');const x=await request('/v1/mcp/calls/'+encodeURIComponent(callId.trim())+'/resume','POST',{});await load();receive(x)});
 const replay=()=>act(async()=>{if(!lastRequest)throw new Error('No request to replay');const x=await request('/v1/mcp/json-rpc','POST',lastRequest);await load();receive(x)});
 const inspectApproval=(row:any)=>act(async()=>{setApprovalDetail(await request('/v1/mcp/approvals/'+encodeURIComponent(row.callId)))});
 const decide=(row:any,status:string)=>act(async()=>{
   const x=await request('/v1/approvals/'+encodeURIComponent(row.approvalId)+'/decision?status='+status,'POST',{});
   setApprovalDetail(null);await load();setMessage(`Approval state: ${x.status}. Execution requires the original caller to resume the saved call.`);
 });
 const security=result?.result?._meta?.['zt.security']||result?._meta?.['zt.security']||result;
 const renderTabs=[...(context?.canManage?[['servers','Upstream servers'],['tools','Tool registration']]:[]),['execute','Execute & inspect'],
   ...(context?.canApprove?[['approvals','MCP approvals']]:[]),['history','Call history']];
 return <div className="mcp-workspace">
  <div className="page-header"><div><h2>MCP Security Gateway</h2><p>Register upstreams and tools, then inspect policy-governed execution.</p></div><button disabled={busy} onClick={()=>load()}>Refresh</button></div>
  <details className="panel mcp-auth"><summary>Caller identity: <strong>{context?.subject||'not authenticated'}</strong></summary>
   <p>Credentials apply only to this MCP workspace and stay in page memory. Switching the navigation role does not change authentication.</p>
   <div className="mcp-form-grid">
    <Field label="Authentication"><select disabled={busy} value={credentialDraft.mode} onChange={e=>setCredentialDraft({mode:e.target.value as McpCredentials['mode']})}><option value="default">Configured dashboard credential</option><option value="service">Registered service client</option><option value="bearer">OIDC bearer token</option></select></Field>
    {credentialDraft.mode==='service'&&<Field label="Client ID"><input disabled={busy} autoComplete="off" value={credentialDraft.clientId||''} onChange={e=>setCredentialDraft({...credentialDraft,clientId:e.target.value})}/></Field>}
    {credentialDraft.mode!=='default'&&<Field label={credentialDraft.mode==='bearer'?'Bearer token':'Client secret'}><input type="password" autoComplete="off" disabled={busy} value={credentialDraft.secret||''} onChange={e=>setCredentialDraft({...credentialDraft,secret:e.target.value})}/></Field>}
   </div><button disabled={busy} onClick={()=>{if(credentialDraft.mode!=='default'&&(!credentialDraft.secret||credentialDraft.mode==='service'&&!credentialDraft.clientId)){setError('Provide the credential and client ID when required');return}setAuth({...credentialDraft});setCredentialDraft({...credentialDraft,secret:''})}}>Apply identity</button>
   <p className="muted">For approvals, request as a registered service client, switch to an independent authorized approver, then switch back to resume. Service clients do not receive approval rights.</p>
  </details>
  {error&&<div className="error-banner" role="alert">{error}</div>}{message&&<p className="mcp-message" role="status">{message}</p>}
  <div className="workflow-tabs" role="tablist">{renderTabs.map(([id,label])=><button key={id} disabled={busy||!context} className={tab===id?'active':''} role="tab" aria-selected={tab===id} onClick={()=>setTab(id)}>{label}{id==='approvals'?` (${approvals.filter(x=>x.status==='PENDING').length})`:''}</button>)}</div>
  {context?.canManage&&tab==='servers'&&<>
   <Panel title="Register upstream"><div className="mcp-form-grid">
    <Field label="Server ID"><input value={serverForm.serverId} disabled={busy} onChange={e=>setServerForm({...serverForm,serverId:e.target.value})}/></Field>
    <Field label="Display name"><input value={serverForm.displayName} disabled={busy} onChange={e=>setServerForm({...serverForm,displayName:e.target.value})}/></Field>
    <Field label="MCP endpoint URL"><input value={serverForm.endpoint} disabled={busy} onChange={e=>setServerForm({...serverForm,endpoint:e.target.value})}/></Field>
    <Field label="Server credential reference"><select disabled={busy} value={serverForm.bearerTokenEnv} onChange={e=>setServerForm({...serverForm,bearerTokenEnv:e.target.value})}><option value="">No upstream authentication</option>{(policy.credentialEnvReferences||[]).map((x:string)=><option key={x}>{x}</option>)}</select></Field>
   </div><div className="mcp-checks"><label><input type="checkbox" disabled={busy} checked={serverForm.enabled} onChange={e=>setServerForm({...serverForm,enabled:e.target.checked})}/> Enabled</label>
    <label><input type="checkbox" disabled={busy||!policy.developmentHttpAvailable} checked={serverForm.developmentHttp} onChange={e=>setServerForm({...serverForm,developmentHttp:e.target.checked})}/> Allow local development HTTP for this connection</label></div>
   <p className="muted">In Docker, use host.docker.internal to reach the host. Allowed registration hosts: {(policy.allowedHosts||[]).join(', ')}. Credential values remain on the server.</p>
   <div className="button-row"><button disabled={busy} onClick={saveServer}>Save upstream</button><button disabled={busy} onClick={()=>setServerForm(newServer())}>Order server example</button></div></Panel>
   <Panel title="Registered connections"><div className="table-scroll"><table><thead><tr><th>Server</th><th>Endpoint</th><th>State</th><th>Credential</th><th>Source</th><th/></tr></thead><tbody>{servers.map(x=><tr key={x.serverId}><td>{x.displayName}<small className="mcp-block">{x.serverId}</small></td><td>{x.endpoint}</td><td>{x.enabled?'ENABLED':'DISABLED'}</td><td>{x.bearerTokenEnv?`${x.bearerTokenEnv} (${x.credentialAvailable?'available':'missing'})`:'None'}</td><td>{x.source}</td><td><button disabled={busy||x.source==='CONFIGURATION'} onClick={()=>setServerForm({serverId:x.serverId,displayName:x.displayName,endpoint:x.endpoint,bearerTokenEnv:x.bearerTokenEnv||'',enabled:x.enabled,developmentHttp:x.developmentHttp})}>Edit</button></td></tr>)}</tbody></table></div><p className="muted">Configuration-defined connections remain deployment-managed. Database connections survive restart; disabling one blocks its bound tools.</p></Panel>
   <Panel title="Load upstream tools"><div className="button-row"><select disabled={busy} value={discoveredServer} onChange={e=>{setDiscoveredServer(e.target.value);setDiscovered([])}}><option value="">Select enabled upstream</option>{servers.filter(x=>x.enabled).map(x=><option key={x.serverId} value={x.serverId}>{x.displayName}</option>)}</select><button disabled={busy||!discoveredServer} onClick={discover}>Connect & load tools</button></div>
    <p className="muted">Discovery initializes a session and calls tools/list. It never calls a business tool.</p>
    {discovered.map(x=><div className="mcp-discovered" key={x.name}><div><strong>{x.name}</strong><p>{x.description}</p></div><button disabled={busy} onClick={()=>importTool(x)}>Register this tool</button></div>)}</Panel>
  </>}
  {context?.canManage&&tab==='tools'&&<>
   <Panel title="Tool registration and execution binding"><div className="mcp-form-grid">
    <Field label="Existing tool or new tool"><select disabled={busy} value={toolForm.toolId} onChange={e=>{const t=catalog.find(x=>x.toolId===e.target.value);setToolForm({...toolForm,toolId:e.target.value,...(t?{name:t.name,description:t.description,riskLevel:t.riskLevel}:{})})}}><option value="">Create a new tool</option>{catalog.filter(x=>x.enabled).map(x=><option key={x.toolId} value={x.toolId}>{x.name}</option>)}</select></Field>
    <Field label="Registered tool name"><input disabled={busy||!!toolForm.toolId} value={toolForm.name} onChange={e=>setToolForm({...toolForm,name:e.target.value})}/></Field>
    <Field label="Upstream server"><select disabled={busy} value={toolForm.serverId} onChange={e=>setToolForm({...toolForm,serverId:e.target.value})}><option value="">Select server</option>{servers.map(x=><option key={x.serverId} value={x.serverId} disabled={!x.enabled}>{x.displayName}{!x.enabled?' (disabled)':''}</option>)}</select></Field>
    <Field label="Upstream tool name"><input disabled={busy} value={toolForm.upstreamTool} onChange={e=>setToolForm({...toolForm,upstreamTool:e.target.value})}/></Field>
    <Field label="Description"><input disabled={busy||!!toolForm.toolId} value={toolForm.description} onChange={e=>setToolForm({...toolForm,description:e.target.value})}/></Field>
    <Field label="Risk level"><select disabled={busy||!!toolForm.toolId} value={toolForm.riskLevel} onChange={e=>setToolForm({...toolForm,riskLevel:e.target.value})}>{['LOW','MEDIUM','HIGH','CRITICAL'].map(x=><option key={x}>{x}</option>)}</select></Field>
    <Field label="Allowed authenticated subjects (one per line)"><textarea disabled={busy} rows={3} value={toolForm.subjects} onChange={e=>setToolForm({...toolForm,subjects:e.target.value})}/></Field>
    <Field label="Input schema (JSON)"><textarea disabled={busy} rows={10} value={toolForm.schema} onChange={e=>setToolForm({...toolForm,schema:e.target.value})}/></Field>
    <Field label="Remove result JSON Pointers (one per line)"><textarea disabled={busy} rows={3} value={toolForm.paths} onChange={e=>setToolForm({...toolForm,paths:e.target.value})}/></Field>
    <Field label="Redact exact text literals (one per line)"><textarea disabled={busy} rows={3} value={toolForm.literals} onChange={e=>setToolForm({...toolForm,literals:e.target.value})}/></Field>
   </div><label className="mcp-checkbox"><input type="checkbox" disabled={busy} checked={toolForm.requireApproval} onChange={e=>setToolForm({...toolForm,requireApproval:e.target.checked})}/> Require independent approval even when policy allows</label>
   <p className="muted">Use api-key for the shared development credential, client:&lt;client-id&gt; for a service client, or the JWT subject. Imported schemas must fit the supported subset. Binding changes invalidate pending execution approval.</p>
   <div className="button-row"><button disabled={busy||!servers.some(x=>x.serverId===toolForm.serverId&&x.enabled)} onClick={saveTool}>Save tool & binding</button><button disabled={busy} onClick={()=>setToolForm({...newTool(),subjects:context.subject})}>New order test tool</button></div></Panel>
   <Panel title="Workspace bindings"><div className="table-scroll"><table><thead><tr><th>Tool</th><th>Upstream</th><th>Allowed subjects</th><th>Approval</th><th/></tr></thead><tbody>{bindings.map(x=><tr key={x.toolId}><td>{catalog.find(t=>t.toolId===x.toolId)?.name||x.toolId}</td><td>{x.config.serverId} / {x.config.upstreamTool}</td><td>{x.config.allowedSubjects.join(', ')}</td><td>{x.config.requireApproval?'Required':'Policy-driven'}</td><td><button disabled={busy} onClick={()=>editBinding(x)}>Edit</button> <button disabled={busy} onClick={()=>act(async()=>{await request('/v1/mcp/tools/'+encodeURIComponent(x.toolId)+'/binding','DELETE');await load();setMessage('Binding removed. The tenant tool definition and previous call records are retained.');})}>Remove binding</button></td></tr>)}</tbody></table></div></Panel>
  </>}
  {tab==='execute'&&<div className="grid-2"><Panel title="Policy-governed tool execution">
   <Field label="Executable tool"><select disabled={busy} value={selected} onChange={e=>setSelected(e.target.value)}><option value="">Select tool</option>{tools.map(x=><option key={x.toolId} value={x.toolId}>{x.name} ({x.riskLevel})</option>)}</select></Field>
   <Field label="Tool arguments (JSON)"><textarea disabled={busy} rows={8} value={argumentsText} onChange={e=>setArgumentsText(e.target.value)}/></Field>
   {!tools.length&&<p>No executable tools for this identity. Register a binding and include this authenticated subject in its permissions.</p>}
   <div className="button-row"><button disabled={busy||!selected} onClick={()=>execute(true)}>Evaluate only</button><button className="primary" disabled={busy||!selected} onClick={()=>execute(false)}>Execute tool</button><button disabled={busy||!lastRequest} onClick={replay}>Replay same request</button></div>
   <Field label="Saved call ID"><input disabled={busy} value={callId} onChange={e=>setCallId(e.target.value)}/></Field><div className="button-row"><button disabled={busy||!callId} onClick={resume}>Resume approved call</button><button disabled={busy||!callId} onClick={()=>act(async()=>{setResult(await request('/v1/mcp/calls/'+encodeURIComponent(callId.trim())));})}>Inspect saved call</button></div>
   <p className="muted">Evaluate only never calls upstream. Execute tool may change business data. UNKNOWN and unfinished EXECUTING outcomes require reconciliation before a new operation.</p>
  </Panel><Panel title="Execution result">
   {security?.status&&<div className="mcp-result"><Status value={security.status}/><strong>{security.decision||''}</strong><p>{security.reason||security.errorCode||''}</p><small>Call: {security.callId||security.id||'—'}</small></div>}
   {result?<pre className="json">{JSON.stringify(result,null,2)}</pre>:<p>Run an evaluation or execution to see its result.</p>}
   <p className="muted">Gateway records do not measure upstream method invocations. Prove zero calls with an upstream counter, and confirm successful business changes in the order server.</p>
  </Panel></div>}
  {context?.canApprove&&tab==='approvals'&&<><Panel title="MCP request approvals"><p>Approve only after reviewing the saved arguments. Self-approval is blocked by the server. Approval does not execute the tool.</p>
   <div className="table-scroll"><table><thead><tr><th>Tool / requester</th><th>State</th><th>Expires</th><th>Call ID</th><th/></tr></thead><tbody>{approvals.map(x=><tr key={x.approvalId}><td>{x.toolName}<small className="mcp-block">{x.requestedBy}</small></td><td><Status value={x.status}/></td><td>{stamp(x.expiresAt)}</td><td><code>{x.callId}</code></td><td><button disabled={busy} onClick={()=>inspectApproval(x)}>Review request</button></td></tr>)}</tbody></table></div>
   {!approvals.length&&<p>No MCP approvals in this workspace.</p>}</Panel>
   {approvalDetail&&<Panel title="Review saved execution request"><p>{approvalDetail.toolName} requested by <strong>{approvalDetail.requestedBy}</strong></p><pre className="json">{JSON.stringify(approvalDetail.arguments,null,2)}</pre><p>{approvalDetail.reason}</p>
    <div className="button-row"><button disabled={busy||approvalDetail.status!=='PENDING'||approvalDetail.requestedBy===context.subject} onClick={()=>decide(approvalDetail,'APPROVED')}>Approve</button><button disabled={busy||approvalDetail.status!=='PENDING'||approvalDetail.requestedBy===context.subject} onClick={()=>decide(approvalDetail,'REJECTED')}>Reject</button></div>
    {approvalDetail.requestedBy===context.subject&&<p>An independent approver must decide this request.</p>}<p>After approval, the original caller can resume call <code>{approvalDetail.callId}</code>.</p></Panel>}</>}
  {tab==='history'&&<Panel title="Recent MCP calls"><p>Up to 100 records in this workspace. Administrators see workspace calls; other callers see their own. Counts describe gateway records, not external executions.</p><div className="table-scroll"><table><thead><tr><th>Time</th><th>Tool / requester</th><th>State</th><th>Error</th><th>Call ID</th><th/></tr></thead><tbody>{history.map(x=><tr key={x.callId}><td>{stamp(x.createdAt)}</td><td>{x.toolName}<small className="mcp-block">{x.requestedBy}</small></td><td><Status value={x.status}/></td><td>{x.errorCode||'—'}</td><td><code>{x.callId}</code></td><td><button disabled={busy||x.requestedBy!==context?.subject} onClick={()=>{setCallId(x.callId);setTab('execute')}}>Open call</button></td></tr>)}</tbody></table></div></Panel>}
  {tab==='history'&&!history.length&&<p className="muted">No committed MCP calls in this scope. A failure during evaluation, including an audit archive failure, can occur before a call record is created.</p>}
 </div>;
}

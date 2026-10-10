import usePageRefresh from '../components/usePageRefresh';
import React from 'react';
import { CheckCircle2, Code2, Copy, FileLock2, Play, Plus, RefreshCw, Save,
    ScanSearch, ShieldAlert, Sparkles, Terminal, XCircle } from 'lucide-react';
import SubjectSelect from '../components/SubjectSelect';
import { TENANT, apiGet, apiPost } from '../api/client';
import { POLICY_EXAMPLE_AMOUNT } from '../config/risk';

type PolicyRow = { id:string;
    name:string;
    version:number;
    priority:number;
    status:string;
    effect:string;
    policyText:string;
    createdAt?:string;
    updatedAt?:string }
;

type Props = { };

const TENANT_ID = TENANT;

const templates: Record<string,string> = {
 'Order reads': 'policy "allow_order_reads" {\n effect allow\n principal.type == "AI_AGENT"\n action == "mcp.tool.call"\n resource.type == "mcp_tool"\n condition { context.mcp.tool == "getOrders" or context.mcp.tool == "getOrderStatus" }\n}',
 'Deny order cancellation': 'policy "deny_test_order_cancel" {\n effect deny\n principal.type == "AI_AGENT"\n action == "mcp.tool.call"\n resource.type == "mcp_tool"\n condition { context.mcp.tool == "cancelOrder" }\n}',
 'Approve order cancellation': 'policy "approve_order_cancel" {\n effect step_up\n principal.type == "AI_AGENT"\n action == "mcp.tool.call"\n resource.type == "mcp_tool"\n condition { context.mcp.tool == "cancelOrder" }\n}', 
  'High-value transfer': `policy "high_value_transfer_protection" {\n  priority 10\n  effect step_up\n
description "Protect high-value AI agent transfers"\n  mode "enforce"\n  tags ["banking",
      "payment", "ai-agent"]\n\n  principal.type == "AI_AGENT"\n  action in ["payment.transfer",
      "payment.refund"]\n  resource.type == "bank_account"\n\n  condition {\n    context.amount >
${POLICY_EXAMPLE_AMOUNT} and (\n      risk.score >= 70 or\n      agent.behavior == "ANOMALOUS"\n    )\n  }
      \n}`,
  'Data exfiltration': `policy "agent_data_exfiltration" {\n  priority 5\n  effect deny\n  description
"Block AI agent export of restricted data to external" +
" destinations"\n  mode "enforce"\n  tags ["ai-agent",
      "data-loss", "exfiltration"]\n\n  principal.type == "AI_AGENT"\n  action == "data.export"\n\n  condition {
          \n    resource.classification in ["CONFIDENTIAL", "RESTRICTED"] and\n    destination.type == "external"\n  }
      \n}`,
  'Least privilege': `policy "agent_least_privilege" {\n  priority 20\n  effect deny\n  description
"Deny anomalous agent access to protected resources"\n  mode "enforce"\n  tags ["zero-trust",
      "least-privilege"]\n\n  principal.type == "AI_AGENT"\n\n  condition {\n    agent.behavior ==
"ANOMALOUS" and\n    risk.score >= 60\n  }
      \n}`
};

const starter = '';

function parsePreview(text:string) {
  const name = text.match(/policy\s+"([^"\\]+)"/)?.[1] || '';
  const effect = text.match(/effect\s+(allow|deny|step_up)/)?.[1] || '';
  const priority = text.match(/priority\s+(-?\d+(?:\.\d+)?)/)?.[1] || '0';
  const description = text.match(/description\s+"([^"\\]*(?:\\.[^"\\]*)*)"/)?.[1] || '';
  const mode = text.match(/mode\s+"([^"\\]+)"/)?.[1] || 'enforce';
  const tags = [...text.matchAll(/tags\s*\[([^\]]*)\]/g)].flatMap(m=>[...m[1].matchAll(/"([^"\\]+)"/g)].map(x=>x[1]));
const matches = text.split(/\r?\n/).map(x=>x.trim()).filter(x=>/^(principal\.type|action|resource\.type)\s+(==|!=|in|contains)/.test(x));
  const condition = text.match(/condition\s*\{([\s\S]*)\}/)?.[1]?.trim() || '';
  return {name,effect,priority:Number(priority),description,mode,tags,matches,condition};
}

function prettyError(e:any){ return e instanceof Error ? e.message : String(e);
}

export default function PolicyStudio(_:Props){
  const [rows,setRows]=React.useState<PolicyRow[]>([]);
  const [name,setName]=React.useState('');
  const [text,setText]=React.useState(starter);
  const [status,setStatus]=React.useState('DRAFT');
  const [message,setMessage]=React.useState('');
  const [busy,setBusy]=React.useState(false);
  const [validation,setValidation]=React.useState<any>(null);
  const [activeTab,setActiveTab]=React.useState<'editor'|'preview'|'simulator'>('editor');
  const [amount,setAmount]=React.useState(15000000);
  const [risk,setRisk]=React.useState(75);
  const [simulation,setSimulation]=React.useState<any>(null);
  const [simSubject,setSimSubject]=React.useState(''),[simScenario,setSimScenario]=React.useState(''),[simOrderId,setSimOrderId]=React.useState('');
  const [simRequest,setSimRequest]=React.useState('{}');

  const preview=React.useMemo(()=>parsePreview(text),[text]);

  const load=React.useCallback(async()=>{
    try { setRows(await apiGet('/v1/policies/all'));
    } catch(e:any) { setMessage(prettyError(e));
    }
  },[]);
  usePageRefresh(load);
 React.useEffect(()=>{load()},[load]);

  const validate=async()=>{
    setBusy(true);
    setMessage('');
    try { const r=await apiPost('/v1/policies/validate',{policyText:text}
        );
        setValidation(r);
        setMessage(r.valid?'Policy DSL validated successfully.':'Policy validation failed.');
    }
    catch(e:any){setValidation({valid:false,error:prettyError(e)});
        setMessage(prettyError(e));
    }
    finally{setBusy(false)}
  };

  const save=async(nextStatus:'DRAFT'|'ACTIVE')=>{
    setBusy(true);
    setMessage('');
    try {
      const checked=await apiPost('/v1/policies/validate',{policyText:text});setValidation(checked);
      if(!checked.valid)throw new Error(checked.error||'Policy validation failed');
      const existing=rows.filter(x=>x.name===name).map(x=>Number(x.version)||0);
      const version=(existing.length?Math.max(...existing):0)+1;
      const r=await apiPost('/v1/policies',{name,version,priority:preview.priority,status:nextStatus,policyText:text});
      setStatus(nextStatus);
      setMessage(`Saved ${r.name} v${r.version} as ${nextStatus}.`);
      await load();
    } catch(e:any){setMessage(prettyError(e));
    }
    finally{setBusy(false)}
  };

  const simulate=async()=>{
    setBusy(true);
    setMessage('');
    try {
      if(!simSubject||!simScenario)throw new Error('Select a simulation identity and scenario first');
      const request=JSON.parse(simRequest);
      request.principal={...request.principal,id:simSubject,type:'AI_AGENT'};
      const r=await apiPost('/v1/policies/simulate-draft',{policyText:text,request});
      setSimulation(r);
      setActiveTab('simulator');
      setMessage('Draft simulation completed.');
    } catch(e:any){setMessage(prettyError(e));
    }
    finally{setBusy(false)}
  };

  const useTemplate=(key:string)=>{setText(templates[key]);
      setName(parsePreview(templates[key]).name);
      setValidation(null);
      setSimulation(null);
      setMessage(`Loaded ${key} template.`)}
  ;

  return <div className="policy-studio">
    <div className="policy-hero">
      <div className="policy-hero-icon">
<FileLock2/>
</div>
      <div className="policy-hero-main">
        <div className="eyebrow-row">
<span className="pill live">POLICY DSL v2</span>
<span className="muted">Tenant-aware · AI-agent aware · deny &gt;
        step_up &gt;
        allow</span>
</div>
        <h2>Policy Studio</h2>
        <p>Write, validate, simulate and publish authorization policies without leaving the security control plane.</p>
      </div>
      <div className="policy-hero-actions">
<button onClick={load}>
<RefreshCw size={
          14}/> Refresh</button>
<button className="primary" onClick={()=>setText(starter)}
      >
<Sparkles size={14}/> New policy</button>
</div>
    </div>

    <div className="policy-layout">
      <aside className="policy-sidebar panel">
        <div className="panel-title-row">
<h3>Templates</h3>
<span className="count">{
            Object.keys(templates).length}
</span>
</div>
        {Object.keys(templates).map(k=>
<button key={k} className="template-item" onClick={
                ()=>useTemplate(k)}>
<Code2 size={14}/>
<span>{k}
</span>
</button>)}
<div className="policy-help">
<ShieldAlert size={15}/>
<div>
<b>Production rule</b>
<span>Validate and simulate before activating. High-risk changes should flow through Lifecycle approval.</span>
</div>
</div>
        <div className="dsl-reference">
<b>DSL supports</b>
<span>priority · metadata · in · contains</span>
<span>and · or · not · parentheses</span>
<span>principal · action · resource</span>
<span>risk · agent · context</span>
</div>
      </aside>

      <section className="policy-main">
        <div className="panel editor-panel">
          <div className="editor-toolbar">
            <div className="editor-tabs">
<button className={activeTab==='editor'?'active':''}
            onClick={()=>setActiveTab('editor')}>Editor</button>
<button className={
                activeTab==='preview'?'active':''} onClick={()=>setActiveTab('preview')}
            >Policy preview</button>
<button className={activeTab==='simulator'?'active':''}
            onClick={()=>setActiveTab('simulator')}>Simulator</button>
</div>
            <div className="button-row compact">
<button onClick={validate}
            disabled={busy}>
<ScanSearch size={14}/> Validate</button>
<button onClick={
                simulate} disabled={busy}>
<Play size={14}/> Simulate</button>
<button onClick={
                ()=>save('DRAFT')} disabled={busy}>
<Save size={14}/> Save draft</button>
<button className="primary" onClick={
                ()=>save('ACTIVE')} disabled={busy}>
<CheckCircle2 size={14}/> Activate</button>
</div>
          </div>
          {activeTab==='editor'&&<>
            <label>Load saved policy
<select value="" onChange={event=>{
  const row=rows.find(policy=>policy.id===event.target.value);
  if(!row)return;
  setName(row.name);setText(row.policyText);setStatus(row.status);
  setValidation(null);setSimulation(null);
  setMessage(`Loaded ${row.name} v${row.version} (${row.status}). Saving creates a new version.`);
}}>
<option value="" disabled>Select a saved policy to view its conditions</option>
{[...rows].sort((a,b)=>a.name.localeCompare(b.name)||b.version-a.version).map(row=>
<option key={row.id} value={row.id}>{row.name} · v{row.version} · {row.status}</option>)}
</select>
</label>
            <div className="policy-meta-grid">
<label>Policy name<input value={
                name} onChange={e=>setName(e.target.value)} />
</label>
<label>Status<select value={
                status} onChange={e=>setStatus(e.target.value)}>
<option>DRAFT</option>
<option>ACTIVE</option>
</select>
</label>
<label>Priority<input type="number" value={
                preview.priority} onChange={e=>setText(text.replace(/priority\s+-?\d+(?:\.\d+)?/,
                `priority ${e.target.value}`))}/>
</label>
</div>
            <textarea className="policy-editor" value={text} onChange={e=>{setText(e.target.value);setValidation(null);setSimulation(null)}} spellCheck={false}/>
            {validation&&<div className={validation.valid?'validation success':'validation failure'}
                >{validation.valid?<CheckCircle2 size={16}/>:<XCircle size={16}/>}
<div>
<b>{
                    validation.valid?'Valid policy':'Invalid policy'}
</b>
<span>{validation.valid?`${
                        validation.effect?.toUpperCase()} · priority ${validation.priority} · ${
                        validation.rules?.length||0} match rules`:validation.error}
</span>
</div>
</div>}
</>}
          {activeTab==='preview'&&<PolicyPreview p={preview} validation={validation}/>} 
          {activeTab==='simulator'&&<><section className="panel"><h3>Draft simulation input</h3><p>Simulation evaluates a hypothetical request. Selecting a caller does not authenticate as that caller or execute a tool.</p>
 <SubjectSelect includeClients value={simSubject} onChange={v=>{setSimSubject(v);setSimulation(null)}}/>
 <label>Scenario<select value={simScenario} onChange={e=>{const v=e.target.value;setSimScenario(v);setSimulation(null);const mcp=v!=='payment';setSimRequest(JSON.stringify({principal:{id:simSubject,type:'AI_AGENT',attributes:{}},action:{name:mcp?'mcp.tool.call':'payment.transfer'},resource:{type:mcp?'mcp_tool':'bank_account',id:mcp?v:'ACC-1001',attributes:{}},context:mcp?{mcp:{tool:v,arguments:v==='getOrders'?{status:'PENDING'}:{orderId:simOrderId}}}:{amount,risk_score:risk}},null,2))}}><option value="">Select scenario</option><option value="getOrders">Order list</option><option value="getOrderStatus">Order status</option><option value="cancelOrder">Order cancellation</option><option value="payment">Payment sample</option></select></label>
 <label>Order ID<input value={simOrderId} onChange={e=>{setSimOrderId(e.target.value);setSimulation(null);try{const r=JSON.parse(simRequest);if(simScenario==='cancelOrder'||simScenario==='getOrderStatus'){r.context.mcp.arguments.orderId=e.target.value;setSimRequest(JSON.stringify(r,null,2))}}catch{}}} placeholder="Enter actual test order ID"/></label>
 <label>Request JSON<textarea rows={12} value={simRequest} onChange={e=>{setSimRequest(e.target.value);setSimulation(null)}}/></label></section><SimulatorPanel amount={amount} setAmount={
                  x=>{setAmount(x);setSimulation(null);try{const r=JSON.parse(simRequest);if(simScenario==='payment'){r.context={...r.context,amount:x};setSimRequest(JSON.stringify(r,null,2))}}catch{}}} risk={risk} setRisk={x=>{setRisk(x);setSimulation(null);try{const r=JSON.parse(simRequest);r.context={...r.context,risk_score:x,'risk.score':x};setSimRequest(JSON.stringify(r,null,2))}catch{}}} run={simulate} result={simulation}
              /></>}
          {message&&<div className="policy-message">{message}
</div>}
</div>

        <div className="grid-2 policy-bottom">
          <div className="panel">
<div className="panel-title-row">
<h3>Decision preview</h3>
<span className={
              `effect-chip ${preview.effect}`}>{preview.effect||'UNSET'}
</span>
</div>
<div className="decision-preview">
<div>
<span>Policy</span>
<b>{
              preview.name||name}
</b>
</div>
<div>
<span>Priority</span>
<b>{preview.priority}
</b>
</div>
<div>
<span>Match rules</span>
<b>{preview.matches.length}
</b>
</div>
<div>
<span>Conditions</span>
<b>{
              preview.condition?preview.condition.split(/\r?\n/).filter(Boolean).length:'0'}
</b>
</div>
</div>
<div className="chips">{preview.tags.map(t=>
<span key={
                  t}>{t}
</span>)}
</div>
</div>
          <div className="panel">
<div className="panel-title-row">
<h3>Policy lifecycle</h3>
<span className="muted">recommended path</span>
</div>
<div className="policy-flow">
<span>Draft</span>
<i>→</i>
<span>Validate</span>
<i>→</i>
<span>Simulate</span>
<i>→</i>
<span>Approve</span>
<i>→</i>
<span className="final">Enforce</span>
</div>
<p className="muted small">Direct activation is available for the demo tenant. Enterprise deployments
should use Lifecycle approval and canary rollout.</p>
</div>
        </div>

        <div className="panel">
<div className="panel-title-row">
<h3>Policy versions</h3>
<button onClick={
            load}>
<RefreshCw size={13}/> Refresh</button>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Policy</th>
<th>Version</th>
<th>Priority</th>
<th>Effect</th>
<th>Status</th>
<th>Updated</th>
</tr>
</thead>
<tbody>{
            rows.filter(r=>r.name===name || !name).slice(0,20).map(r=>
<tr key={r.id}
            >
<td>
<b>{r.name}
</b>
</td>
<td>v{r.version}
</td>
<td>{r.priority}
</td>
<td>
<span className={
                `effect-chip ${r.effect}`}>{r.effect}
</span>
</td>
<td>
<span className="status-pill">{
                r.status}
</span>
</td>
<td>{r.updatedAt?new Date(r.updatedAt).toLocaleString():'—'}
</td>
</tr>)}
</tbody>
</table>
</div>{!rows.filter(r=>r.name===name || !name).length&&<div className="empty">
<FileLock2 size={
                24}/>
<b>No versions yet</b>
<small>Save this policy to create the first version.</small>
</div>}
</div>
      </section>
    </div>
  </div>
}

function PolicyPreview({p,validation}:{p:any;validation:any}){
  return <div className="policy-preview">
<div className="preview-grid">
<div>
<span>Name</span>
<b>{
      p.name||'—'}
</b>
</div>
<div>
<span>Effect</span>
<b className={`effect-text ${
          p.effect}`}>{p.effect||'—'}
</b>
</div>
<div>
<span>Priority</span>
<b>{p.priority}
</b>
</div>
<div>
<span>Mode</span>
<b>{p.mode}
</b>
</div>
</div>
<div className="preview-section">
<small>DESCRIPTION</small>
<p>{
      p.description||'No description.'}
</p>
</div>
<div className="preview-section">
<small>MATCH RULES</small>{
      p.matches.map((x:string,i:number)=>
<code key={i}>{x}
</code>)}{!p.matches.length&&<p className="muted">No principal/action/resource match rules.</p>}
</div>
<div className="preview-section">
<small>CONDITION AST PREVIEW</small>
<pre className="json">{
      JSON.stringify({condition:p.condition||null,tags:p.tags},null,2)}
</pre>
</div>{
      validation&&<div className={validation.valid?'validation success':'validation failure'}
      >{validation.valid?<CheckCircle2 size={16}/>:<XCircle size={16}/>}
<span>{
          validation.valid?'Backend parser accepted this policy.':validation.error}
</span>
</div>}
</div>
}

function SimulatorPanel({amount,setAmount,risk,setRisk,run,result}:{amount:number;
    setAmount:(x:number)=>void;
    risk:number;
    setRisk:(x:number)=>void;
    run:()=>void;
    result:any}){
  return <div className="policy-sim">
<div className="sim-form">
<label>Amount<input type="number" value={
      amount} onChange={e=>setAmount(Number(e.target.value))}/>
</label>
<label>Risk score<input type="number" min="0" max="100" value={
      risk} onChange={e=>setRisk(Number(e.target.value))}/>
</label>
<button className="primary" onClick={
      run}>
<Play size={14}/> Run draft simulation</button>
</div>{result?<pre className="json">{
          JSON.stringify(result,null,2)}
</pre>:<div className="empty">
<Terminal size={
          24}/>
<b>No simulation yet</b>
<small>Select an identity and scenario, then review the JSON request.</small>
</div>}
</div>
}

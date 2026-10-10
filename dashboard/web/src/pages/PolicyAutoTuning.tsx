import usePageRefresh from '../components/usePageRefresh';
import React from 'react';
import { AlertTriangle, CheckCircle2, FlaskConical, RefreshCw, ShieldCheck } from 'lucide-react';
import SubjectSelect from '../components/SubjectSelect';
import { apiGet, apiPost } from '../api/client';

const sampleRequest = {
  principal:{id:'',type:'AI_AGENT',attributes:{}},
  action:{name:'read'},
  resource:{type:'payment',id:'txn-001',attributes:{}},
  context:{source:'3.19-tuning-simulation'}
};

export default function PolicyAutoTuning(){
 const [candidates,setCandidates]=React.useState<any[]>([]),[policies,
 setPolicies]=React.useState<any[]>([]),[proposals,setProposals]=React.useState<any[]>([]);
 const [selected,setSelected]=React.useState<any>(null),[base,setBase]=React.useState(''),
 [request,setRequest]=React.useState(JSON.stringify(sampleRequest,null,
 2));
 const [msg,setMsg]=React.useState(''),[err,setErr]=React.useState(''),[busy,setBusy]=React.useState(false);
 const load=async()=>{try{const [c,p,r]=await Promise.all([apiGet('/v1/security/control-loop/policy-tuning/candidates'),
         apiGet('/v1/policies/all'),apiGet('/v1/security/control-loop/policy-tuning/proposals')]);
setCandidates(c);
setPolicies(p);
setProposals(r);
if(!base&&p.length)setBase(p.find((x:any)=>x.status==='PUBLISHED'||x.status==='ACTIVE')?.id||p[0].id);
         setErr('')}catch(e:any){setErr(e.message)}};
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const propose=async(c:any)=>{if(!base){setErr('Select a published/active base policy first.');
         return}setBusy(true);
         try{const r=await apiPost('/v1/security/control-loop/policy-tuning/proposals',
         {profileId:c.profileId,basePolicyId:base,requestedBy:'dashboard-admin'}
         );
         setSelected(r);
         setMsg('Tuning proposal created. Simulation is required before approval.');
         await load()}catch(e:any){setErr(e.message)}finally{setBusy(false)}};
 const simulate=async(p:any)=>{setBusy(true);
     try{let req;
         try{req=JSON.parse(request);if(!req.principal?.id)throw new Error('Select a simulation identity first')}
         catch{throw new Error('Simulation request JSON is invalid')}const r=await
apiPost(`/v1/security/control-loop/policy-tuning/proposals/${
             p.proposalId}/simulate`,req);setSelected(r);setMsg('Simulation completed. Review the returned state before approving.');
         await load()}catch(e:any){setErr(e.message)}finally{setBusy(false)}};
const approve=async(p:any)=>{setBusy(true);
    try{const r=await apiPost(`/v1/security/control-loop/policy-tuning/proposals/${
p.proposalId}/approve?actor=dashboard-admin`,{});setSelected(r);setMsg('Approved: a new DRAFT policy version was created. It was not published.');
         await load()}catch(e:any){setErr(e.message)}finally{setBusy(false)}};
 const reject=async(p:any)=>{setBusy(true);
     try{await apiPost(`/v1/security/control-loop/policy-tuning/proposals/${
             p.proposalId}/reject?actor=dashboard-admin`,{});setMsg('Proposal rejected.');
         setSelected(null);
         await load()}catch(e:any){setErr(e.message)}finally{
         setBusy(false)}};
 return <div>
<div className="page-header">
<div>
<h2>Policy Auto-Tuning</h2><SubjectSelect includeClients value={(()=>{try{return JSON.parse(request).principal?.id||''}catch{return ''}})()} onChange={value=>{try{const r=JSON.parse(request);r.principal={...r.principal,id:value};setRequest(JSON.stringify(r,null,2));setSelected(null)}catch{setErr('Fix request JSON before choosing an identity')}}}/>
<p>Effectiveness evidence → simulation → human approval → DRAFT. Publishing remains a separate action.</p>
</div>
<button onClick={
     load}>
<RefreshCw size={14}/> Refresh</button>
</div>
 {err&&<div className="error-banner">
<AlertTriangle size={17}/>{err}
</div>}
 {msg&&<div className="notice">
<CheckCircle2 size={16}/>{msg}
</div>}
<section className="panel">
<div className="panel-head">
<h3>Tuning baseline</h3>
<span className="muted">Only published/active policies can be used as a baseline.</span>
</div>
<div className="inline-form">
<label>Base policy <select value={
     base} onChange={e=>setBase(e.target.value)}>
<option value="">Select policy…</option>{
     policies.filter((p:any)=>p.status==='PUBLISHED'||p.status==='ACTIVE').map((p:any)=>
<option key={
         p.id} value={p.id}>{p.name} v{p.version} · priority {p.priority}
</option>)}
</select>
</label>
<label>Simulation request <textarea rows={4} value={request}
 onChange={e=>setRequest(e.target.value)}/>
</label>
</div>
</section>
 <section className="panel">
<div className="panel-head">
<h3>High-confidence tuning candidates</h3>
<span className="muted">3.18 effectiveness profiles only · advisory until proposed</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Action</th>
<th>Target</th>
<th>Score</th>
<th>Confidence</th>
<th>Evidence</th>
<th>Suggested change</th>
<th/>
</tr>
</thead>
<tbody>{
     candidates.map(c=>
<tr key={c.profileId}>
<td>
<b>{c.actionType}
</b>
</td>
<td>{
         c.target}
</td>
<td>{c.effectivenessScore}%</td>
<td>{c.confidence}
</td>
<td>{
         c.evidenceCount}
</td>
<td>Priority {c.suggestedPriorityDelta}
</td>
<td>
<button disabled={
         busy} onClick={()=>propose(c)}>Propose tuning</button>
</td>
</tr>)}
</tbody>
</table>
</div>{
     !candidates.length&&<div className="muted">No candidate has enough evidence/confidence yet.</div>}
</section>
 <section className="panel">
<div className="panel-head">
<h3>Proposal queue</h3>
<span className="muted">Simulation gate + explicit approval</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Status</th>
<th>Base</th>
<th>Priority</th>
<th>Simulation</th>
<th>Reason</th>
<th>Actions</th>
</tr>
</thead>
<tbody>{
     proposals.map(p=>
<tr key={p.proposalId}>
<td>
<b>{p.status}
</b>
</td>
<td>{
         p.basePolicyId?.slice(0,8)}… v{p.basePolicyVersion}
</td>
<td>{p.proposedPriority}
     ({p.priorityDelta})</td>
<td>{p.simulationStatus}
</td>
<td>{p.reason}
</td>
<td>{
         p.status==='PENDING'&&<>
<button disabled={busy} onClick={()=>simulate(p)}
         >
<FlaskConical size={13}/> Simulate</button>{p.simulationStatus==='PASSED'&&<button disabled={
                 busy} onClick={()=>approve(p)}>
<ShieldCheck size={13}/> Approve to DRAFT</button>}
<button disabled={busy} onClick={()=>reject(p)}>Reject</button>
</>}
</td>
</tr>)}
</tbody>
</table>
</div>
</section>
 <section className="panel">
<div className="panel-head">
<h3>Safety boundary</h3>
</div>
<div className="grid-2">
<div>
<h4>Automated</h4>
<ul>
<li>Effectiveness-based candidate ranking</li>
<li>Small priority adjustment proposal</li>
<li>Policy DSL validation</li>
<li>What-if simulation</li>
</ul>
</div>
<div>
<h4>Human controlled</h4>
<ul>
<li>Approve tuning</li>
<li>Publish the resulting DRAFT</li>
<li>Change policy conditions</li>
<li>Enforce/isolate agents</li>
</ul>
</div>
</div>
</section>
 </div>
}

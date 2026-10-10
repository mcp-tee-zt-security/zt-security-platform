import usePageRefresh from '../components/usePageRefresh';
import RecordLinks from '../components/RecordLinks';
import React from 'react';
import { apiGet, apiPost } from '../api/client';

type Props = { subject?:string; initialView?:'pending'|'history'|'executions'; onAgent?:(subject:string)=>void };
const stamp=(value:any)=>value?new Date(value).toLocaleString():'—';
const Badge=({value}:{value:any})=><span className={`status ${String(value||'unknown').toLowerCase()}`}>{value||'UNKNOWN'}</span>;

export default function ApprovalExecution({subject='',initialView='pending',onAgent}:Props){
 const [view,setView]=React.useState(initialView);
 const [approvals,setApprovals]=React.useState<any[]>([]),[contracts,setContracts]=React.useState<any[]>([]);
 const [error,setError]=React.useState(''),[busy,setBusy]=React.useState(false),[acting,setActing]=React.useState(false);
 const [selection,setSelection]=React.useState<any>(null),[detail,setDetail]=React.useState<any>(null);
 const [detailError,setDetailError]=React.useState(''),[detailBusy,setDetailBusy]=React.useState(false);
 const detailRequest=React.useRef(0);
 const loadRequest=React.useRef(0);
 React.useEffect(()=>()=>{loadRequest.current++;detailRequest.current++},[]);
 React.useEffect(()=>setView(initialView),[initialView]);
 const load=React.useCallback(async()=>{
   const ticket=++loadRequest.current;setBusy(true);setError('');
   try{
     const filter=subject?`?subject=${encodeURIComponent(subject)}`:'';
     const [a,c]=await Promise.all([apiGet('/v1/workflows/approvals'+filter),apiGet('/v1/workflows/contracts'+filter)]);
     if(loadRequest.current===ticket){setApprovals(a);setContracts(c)}
   }catch(e:any){if(loadRequest.current===ticket){setApprovals([]);setContracts([]);setError(e.message)}}finally{if(loadRequest.current===ticket)setBusy(false)}
 },[subject]);
 usePageRefresh(load);
 React.useEffect(()=>{detailRequest.current++;setSelection(null);setDetail(null);setDetailError('');load()},[load]);
 const inspect=async(kind:'approval'|'contract',row:any)=>{
   const ticket=++detailRequest.current;
   setSelection({kind,row});setDetail(null);setDetailError('');setDetailBusy(true);
   try{
     const result=await apiGet(kind==='approval'?`/v1/workflows/requests/${encodeURIComponent(row.requestId)}`:`/v1/workflows/contracts/${encodeURIComponent(row.id)}`);
     if(detailRequest.current===ticket)setDetail(result);
   }catch(e:any){if(detailRequest.current===ticket)setDetailError(e.message)}
   finally{if(detailRequest.current===ticket)setDetailBusy(false)}
 };
 const decide=async(row:any,status:string)=>{
   setActing(true);setDetailError('');
   try{
     const updated=await apiPost(`/v1/approvals/${encodeURIComponent(row.id)}/decision?status=${status}`,{});
     if(updated.status!==status)setDetailError(`The server returned ${updated.status}; the approval was not ${status.toLowerCase()}.`);
     await load();await inspect('approval',updated);
     if(updated.status!==status)setDetailError(`The server returned ${updated.status}; refresh and review the request.`);
   }catch(e:any){setDetailError(e.message)}finally{setActing(false)}
 };
 const queue=approvals.filter(row=>view==='pending'?row.status==='PENDING':row.status!=='PENDING');
 const approval=selection?.kind==='approval'?selection.row:detail?.approval;
 const selectedContracts=selection?.kind==='approval'?detail?.contracts||[]:detail?.contract?[detail.contract]:[];
 const agent=detail?.decision?.subject||detail?.contract?.subject||subject;
 return <div className="workflow-workspace"><RecordLinks/>
  <div className="page-header"><div><h2>Approvals & Execution</h2><p>Follow the stored decision, human approval, execution contract and verified outcome.</p></div><button onClick={load} disabled={busy}>Refresh records</button></div>
  {subject&&<div className="workflow-context">Agent filter: <strong>{subject}</strong></div>}
  <div className="workflow-tabs" role="tablist" aria-label="Approval and execution records">
   {(['pending','history','executions'] as const).map(key=><button key={key} role="tab" aria-selected={view===key} aria-controls="workflow-records" id={`workflow-tab-${key}`} className={view===key?'active':''} onClick={()=>setView(key)}>{key==='pending'?'Pending approvals':key==='history'?'Approval history':'Execution records'}</button>)}
  </div>
  {error&&<div className="error-banner" role="alert">Records could not be loaded: {error}</div>}
  <section className="panel" id="workflow-records" role="tabpanel" aria-labelledby={`workflow-tab-${view}`}>
   <p className="muted small">Up to 100 newest records in the current tenant/workspace. Approvals without a stored governance decision are not included.</p>
   {busy?<p role="status">Loading records…</p>:!error&&(view==='executions'?<>
    <table><thead><tr><th>Agent / action</th><th>Resource</th><th>Status</th><th>Created</th><th>Details</th></tr></thead><tbody>{contracts.map(row=><tr key={row.id}><td><b>{row.subject}</b><br/>{row.action}</td><td>{row.resource}</td><td><Badge value={row.status}/></td><td>{stamp(row.createdAt)}</td><td><button disabled={acting} onClick={()=>inspect('contract',row)}>View execution</button></td></tr>)}</tbody></table>
    {!contracts.length&&<p className="empty">No execution contracts found.</p>}
   </>:<>
    <table><thead><tr><th>Request</th><th>Approver</th><th>Status</th><th>Reason</th><th>Created</th><th>Details</th></tr></thead><tbody>{queue.map(row=><tr key={row.id}><td className="mono">{row.requestId}</td><td>{row.approverId||'Unassigned'}</td><td><Badge value={row.status}/></td><td>{row.reason||'—'}</td><td>{stamp(row.createdAt)}</td><td><button disabled={acting} onClick={()=>inspect('approval',row)}>Review request</button></td></tr>)}</tbody></table>
    {!queue.length&&<p className="empty">{view==='pending'?'No pending approvals.':'No completed approvals.'}</p>}
   </>)}
  </section>
  {selection&&<section className="panel workflow-detail" aria-label="Request details">
   <div className="panel-head"><h3>Request → Approval → Execution → Verification</h3><button disabled={acting} onClick={()=>{detailRequest.current++;setSelection(null);setDetail(null)}}>Close details</button></div>
   {detailBusy&&<p role="status">Loading linked records…</p>}
   {detailError&&<div className="error-banner" role="alert">{detailError}</div>}
   <div className="workflow-stage-grid">
    <section><h4>1. Policy decision</h4>{detail?.decision?<><Badge value={detail.decision.decision}/><p><b>{detail.decision.subject}</b><br/>{detail.decision.action}<br/>{detail.decision.resource}</p><p>{detail.decision.reason}</p><small>Request: {detail.decision.requestId}</small>{agent&&onAgent&&<p><button onClick={()=>onAgent(agent)}>Open agent</button></p>}</>:<p>Stored decision unavailable.</p>}</section>
    <section><h4>2. Human approval</h4>{approval?<><Badge value={approval.status}/><p>Assigned: {approval.approverId||'Unassigned'}<br/>Decided by: {approval.decidedBy||'—'}<br/>Expires: {stamp(approval.expiresAt)}</p><p>{approval.reason}</p>{selection.kind==='approval'&&approval.status==='PENDING'&&<div className="workflow-actions"><button disabled={acting||detailBusy||!detail?.decision} onClick={()=>decide(approval,'APPROVED')}>Approve request</button><button disabled={acting||detailBusy||!detail?.decision} onClick={()=>decide(approval,'REJECTED')}>Reject request</button></div>}</>:<p>No approval linked to this contract.</p>}<small>Approval does not automatically execute an action.</small></section>
    <section><h4>3. Execution</h4>{selectedContracts.length?selectedContracts.map((row:any)=><div key={row.id}><Badge value={row.status}/><p>{row.action}<br/>{row.resource}</p><small>Contract: {row.id}</small>{selection.kind==='approval'&&<p><button disabled={acting} onClick={()=>inspect('contract',row)}>Inspect execution</button></p>}</div>):<p>No execution contract has been created.</p>}{detail?.execution&&<p>Started: {stamp(detail.execution.startedAt)}<br/>Completed: {stamp(detail.execution.completedAt)}</p>}<small>This view never triggers execution.</small></section>
    <section><h4>4. Verification</h4>{detail?.verifications?.length?detail.verifications.map((row:any)=><div key={row.id}><Badge value={row.verified===true?'VERIFIED':'NOT_VERIFIED'}/><p>{row.reason}</p><small>{stamp(row.createdAt)}</small></div>):<p>{selection.kind==='approval'&&selectedContracts.length?'Inspect an execution to see its verification records.':'No verification record is available.'}</p>}<small>Execution success and verified outcome are separate states.</small></section>
   </div>
  </section>}
 </div>;
}

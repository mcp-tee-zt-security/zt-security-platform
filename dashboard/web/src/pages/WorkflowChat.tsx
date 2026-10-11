import React from 'react';
import {apiGet,apiPost} from '../api/client';
import {notifyAction} from '../components/ActionFeedback';
import usePageRefresh from '../components/usePageRefresh';

const state=(entry:any)=>entry.execution?.status||entry.approval?.status||entry.decision?.decision||'PROPOSED';
const expired=(value:string|undefined)=>!!value&&new Date(value).getTime()<=Date.now();

export default function WorkflowChat(){
 const [session,setSession]=React.useState<any>(null),[entries,setEntries]=React.useState<any[]>([]);
 const [message,setMessage]=React.useState(''),[busy,setBusy]=React.useState(false),[error,setError]=React.useState(''),[verification,setVerification]=React.useState<Record<string,any>>({});
 const request=React.useRef(0),inFlight=React.useRef(false);
 const conversation=React.useRef<HTMLDivElement>(null);
 const [pendingMessage,setPendingMessage]=React.useState('');
 const [fileReplies,setFileReplies]=React.useState<Record<string,any>>({});
 React.useEffect(()=>{setFileReplies({})},[session?.subject]);
 React.useEffect(()=>{const timer=window.setInterval(()=>setFileReplies(previous=>Object.fromEntries(Object.entries(previous).filter(([,reply])=>reply.expiresAt>Date.now()))),5000);return()=>clearInterval(timer)},[]);
 React.useEffect(()=>{const view=conversation.current;if(view)view.scrollTop=view.scrollHeight},[entries.length,pendingMessage]);
 const load=React.useCallback(async()=>{const ticket=++request.current;try{const [s,e]=await Promise.all([apiGet('/v1/session'),apiGet('/v1/workflow-chat/proposals')]);if(ticket===request.current){setSession(s);setEntries(e);setVerification(previous=>{const next={...previous};for(const entry of e){if(entry.verifications?.length)next[entry.proposal.id]=entry.verifications[0]}return next});setError('')}return true}catch(e:any){if(ticket===request.current)setError(e.message);return false}},[]);
 React.useEffect(()=>{void load();const timer=window.setInterval(()=>{if(!inFlight.current)void load()},15000);return()=>{request.current++;clearInterval(timer)}},[load]);
 usePageRefresh(()=>{if(!inFlight.current)void load()});
 const run=async(label:string,job:()=>Promise<void>)=>{if(inFlight.current)return;inFlight.current=true;setBusy(true);setError('');notifyAction('running',label+'…');try{await job();const refreshed=await load();notifyAction(refreshed?'success':'warning',label+(refreshed?' completed':' completed; records could not be refreshed'))}catch(e:any){setError(e.message);notifyAction('error',label+': '+e.message)}finally{inFlight.current=false;setBusy(false)}};
 const send=async()=>{const text=message.trim();if(!text||inFlight.current||!session?.canRequest)return;setPendingMessage(text);try{await run('Assistant response',async()=>{await apiPost('/v1/workflow-chat/messages',{message:text});setMessage('')})}finally{setPendingMessage('')}};
 const execute=(entry:any)=>run('Execute simulated transfer',async()=>{const result=await apiPost(`/v1/execution/contracts/${entry.contract.id}/execute`);if(result.status!=='SUCCEEDED'){await load();throw Error('Execution state: '+result.status)}});
 const verify=(entry:any)=>run('Verify stored transaction',async()=>{const result=await apiPost(`/v1/execution/${entry.execution.id}/verify`);setVerification(v=>({...v,[entry.proposal.id]:result}));if(!result.verified)throw Error(result.reason)});
 const retrieve=(entry:any)=>run('Evaluate file access',async()=>{setFileReplies(previous=>{const next={...previous};delete next[entry.proposal.id];return next});try{const result=await apiPost(`/v1/workflow-chat/proposals/${entry.proposal.id}/retrieve`);setFileReplies(previous=>({...previous,[entry.proposal.id]:{...result,expiresAt:Date.now()+60000}}))}catch(e:any){setFileReplies(previous=>({...previous,[entry.proposal.id]:{decision:'UNAVAILABLE',reason:e.message,results:[],expiresAt:Date.now()+60000}}));throw e}});
 return <div className="workflow-chat"><div className="page-header"><div><h2>Workflow Chat</h2><p>Ask a question or propose a task. ZT controls access and execution.</p></div><button disabled={busy} onClick={()=>void load()}>Refresh requests</button></div>
  {error&&<div className="error-banner" role="alert">{error}</div>}
  <section className="workflow-chat-window" aria-label="Chat with the workflow agent">
  <div className="workflow-conversation" ref={conversation}>{!entries.length&&!pendingMessage&&<div className="workflow-chat-empty"><h3>How can I help?</h3><p>Ask a question, prepare a EUR transfer, or request a registered file.</p></div>}{[...entries].sort((a,b)=>new Date(a.proposal.createdAt).getTime()-new Date(b.proposal.createdAt).getTime()).map(entry=>{const p=entry.proposal,transfer=p.proposal,v=verification[p.id],file=fileReplies[p.id];const type=p.type||'TRANSFER';const ready=!!entry.contract&&!entry.execution&&!expired(entry.contract.expiresAt)&&(entry.decision?.decision==='ALLOW'||entry.approval?.status==='APPROVED');return <article className="workflow-chat-turn" key={p.id}>
   <div className="chat-user"><small>Requested by {p.requestedBy} · {new Date(p.createdAt).toLocaleString()}</small><p>{p.message}</p></div>
   <div className="chat-agent"><strong>Workflow assistant</strong>{p.answer&&<p className="workflow-chat-answer">{p.answer}</p>}
    {type==='RETRIEVAL'&&<><p>File request: <strong>{p.target}</strong>. Confirm to evaluate your source permissions and the AI's active ZT policies.</p>{session?.canRequest&&session.subject===p.requestedBy&&<button disabled={busy||expired(p.expiresAt)} onClick={()=>retrieve(entry)}>Check ZT access / retrieve one file</button>}{file&&<div className="workflow-file-response" role="status"><p><strong>{file.decision}</strong> · {file.reason}</p>{file.results?.map((row:any)=><section key={row.resource_id}><h4>{row.title}</h4><pre>{row.content||'This registered file has no text preview.'}</pre></section>)}{file.results?.length>0&&<small>Authorized source content · preview clears after one minute; a new read checks permissions again.</small>}</div>}</>}
    {type==='TRANSFER'&&<><p>Review the extracted values before submitting.</p><div className="workflow-stage-grid"><section><h4>Recipient</h4>{transfer.recipient}</section><section><h4>Amount</h4>{transfer.amount} {transfer.currency}</section><section><h4>ZT state</h4><span className="status">{state(entry)}</span></section><section><h4>Execution</h4>{entry.execution?'Recorded transaction':'Not executed'}</section></div>
    {!entry.decision&&session?.canRequest&&session.subject===p.requestedBy&&<button disabled={busy||expired(p.expiresAt)} onClick={()=>run('Confirm parameters and evaluate ZT policy',async()=>{await apiPost(`/v1/workflow-chat/proposals/${p.id}/submit`)})}>Confirm these parameters / request ZT evaluation</button>}
    {entry.decision&&<p>ZT: {entry.decision.reason}</p>}{entry.approval&&<p>Approval: {entry.approval.status} {entry.approval.decidedBy&&`· Decided by ${entry.approval.decidedBy}`}</p>}
    {entry.approval?.status==='PENDING'&&<p>No execution yet. An independent approver must review this request in the approval inbox.</p>}
    {ready&&session?.canExecute&&<button disabled={busy} onClick={()=>execute(entry)}>Execute saved simulated transfer</button>}{ready&&!session?.canExecute&&<p>Ready for an executor to run the saved contract.</p>}
    {entry.execution&&<><h4>Simulated transaction result</h4><pre className="json">{JSON.stringify(entry.execution.result,null,2)}</pre><p>Executed by: {entry.execution.executedBy}</p>{entry.execution.status==='SUCCEEDED'&&session?.canExecute&&<button disabled={busy} onClick={()=>verify(entry)}>Verify transaction in simulator ledger</button>}{v&&<p>Verification: {v.verified?'VERIFIED':'NOT VERIFIED'} · {v.reason}</p>}</>}
    <small>Proposal: {p.id}{entry.contract&&` · Contract: ${entry.contract.id}`}</small></>}
   </div></article>})}{pendingMessage&&<article className="workflow-chat-turn"><div className="chat-user"><small>You</small><p>{pendingMessage}</p></div><div className="chat-agent" role="status">Preparing your request…</div></article>}</div>
  <form className="workflow-chat-composer" onSubmit={e=>{e.preventDefault();void send()}}>
   <textarea aria-label="Your request" rows={4} disabled={busy||!session?.canRequest} value={message} onChange={e=>setMessage(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();void send()}}} placeholder="Ask the workflow agent…"/>
   <div className="workflow-composer-footer"><span>{!session?.canRequest?'Sign in as a requester to send a message.':'Enter to send · Shift + Enter for a new line'}</span><button className="workflow-chat-send" type="submit" disabled={busy||!session?.canRequest||!message.trim()} aria-label={busy?'Processing request':'Send message'} title="Send message"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg></button></div>
  </form></section>
 </div>;
}

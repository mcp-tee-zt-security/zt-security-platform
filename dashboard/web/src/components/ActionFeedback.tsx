import React from 'react';

export type FeedbackKind='running'|'success'|'warning'|'error';
type Feedback={kind:FeedbackKind;message:string};
let latest:Feedback|null=null;
let dismissTimer:number|undefined;
export function notifyAction(kind:FeedbackKind,message:string){
 if(dismissTimer!==undefined)window.clearTimeout(dismissTimer);
 dismissTimer=undefined;
 latest={kind,message};
 window.dispatchEvent(new CustomEvent('zt-action-feedback',{detail:latest}));
 if(kind!=='running'){
  const current=latest;
  dismissTimer=window.setTimeout(()=>{
   if(latest!==current)return;
   latest=null;dismissTimer=undefined;
   window.dispatchEvent(new CustomEvent('zt-action-feedback',{detail:null}));
  },kind==='error'?8000:5000);
 }
}
export function summarizeResponse(value:any):{kind:FeedbackKind;message:string}{
 if(value?.error)return {kind:'error',message:'요청 처리 실패: '+(value.error.message||String(value.error))};
 let data=value?.toolResponse||value;
 const rpc=value?.result;
 if(rpc?.content){try{const text=rpc.content.find((item:any)=>item.type==='text')?.text;const parsed=JSON.parse(text);if(parsed&&typeof parsed==='object')data=parsed}catch{/* Plain MCP text remains in the response panel. */}}
 const decision=data?.decision||rpc?._meta?.['zt.security']?.decision;
 if(decision==='DENY'||decision==='STEP_UP')return {kind:'warning',message:`요청 완료 · ZT ${decision} · 자료 접근 차단${data?.reason?' · '+data.reason:''}`};
 if(['ERROR','REJECTED'].includes(decision)||rpc?.isError)return {kind:'error',message:`요청 거절/오류${data?.dependencyStatus?' · HTTP '+data.dependencyStatus:''} · 상세 응답을 확인하세요.`};
 if(decision==='ALLOW')return {kind:'success',message:'요청 완료 · ALLOW'+(Array.isArray(data.results)?` · 반환 ${data.results.length}건${data.results.length===0?' (검색/조회 결과 없음)':''}`:'')};
 return {kind:'success',message:'요청 완료'};
}
export default function ActionToastHost(){
 const [feedback,setFeedback]=React.useState<Feedback|null>(latest);
 React.useEffect(()=>{const onFeedback=(event:Event)=>setFeedback((event as CustomEvent<Feedback|null>).detail);window.addEventListener('zt-action-feedback',onFeedback);setFeedback(latest);return()=>window.removeEventListener('zt-action-feedback',onFeedback)},[]);
 if(!feedback)return null;
 return <div className={'action-feedback-toast '+feedback.kind} role={feedback.kind==='error'?'alert':'status'} aria-live={feedback.kind==='error'?'assertive':'polite'}><span className="action-feedback-icon" aria-hidden="true">{feedback.kind==='running'?'◌':feedback.kind==='success'?'✓':feedback.kind==='warning'?'!':'×'}</span><span>{feedback.message}</span></div>;
}

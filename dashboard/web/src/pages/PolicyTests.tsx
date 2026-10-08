import React from 'react';
import PolicyWhatIf from './PolicyWhatIf';
import ShadowReplay from './ShadowReplay';
import { POLICY_EXAMPLE_AMOUNT } from '../config/risk';

type Mode='impact'|'replay'|'live';
export default function PolicyTests({initialMode='impact',renderLive}:{initialMode?:Mode;renderLive:React.ReactNode}){
 const [mode,setMode]=React.useState<Mode>(initialMode);
 const [policy,setPolicy]=React.useState(`policy "agent_high_value_guard" {
  priority 20
  effect step_up
  principal.type == "AI_AGENT"
  action == "payment.transfer"
  resource.type == "bank_account"
  condition { context.amount > ${POLICY_EXAMPLE_AMOUNT} }
}`);
 React.useEffect(()=>setMode(initialMode),[initialMode]);
 const modes:[Mode,string,string][]=[['impact','Change impact','Compare the proposed policy with current decisions.'],['replay','Historical replay','Replay past events against the same proposed policy.'],['live','Live evaluation','Evaluate a request through the live pipeline. This writes decision and audit records.']];
 return <div>
  <div className="page-header"><div><h2>Policy Tests</h2><p>Choose a test method. Change impact and historical replay share the proposed policy text.</p></div></div>
  <div className="workflow-tabs" role="tablist" aria-label="Policy test methods">{modes.map(([key,label])=><button key={key} role="tab" id={`test-tab-${key}`} aria-controls={`test-panel-${key}`} aria-selected={mode===key} className={mode===key?'active':''} onClick={()=>setMode(key)}>{label}</button>)}</div>
  <p className={mode==='live'?'workflow-context':'muted'}>{modes.find(([key])=>key===mode)?.[2]}</p>
  <div id="test-panel-impact" role="tabpanel" aria-labelledby="test-tab-impact" hidden={mode!=='impact'}><PolicyWhatIf policyText={policy} onPolicyTextChange={setPolicy}/></div>
  <div id="test-panel-replay" role="tabpanel" aria-labelledby="test-tab-replay" hidden={mode!=='replay'}><ShadowReplay policyText={policy} onPolicyTextChange={setPolicy}/></div>
  <div id="test-panel-live" role="tabpanel" aria-labelledby="test-tab-live" hidden={mode!=='live'}>{renderLive}</div>
 </div>;
}

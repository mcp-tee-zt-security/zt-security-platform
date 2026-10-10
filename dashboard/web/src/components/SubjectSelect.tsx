import React from 'react';
import {apiGet,WORKSPACE} from '../api/client';

export default function SubjectSelect({value,onChange,includeClients=false,disabled=false}:{value:string;onChange:(value:string)=>void;includeClients?:boolean;disabled?:boolean}){
 const [agents,setAgents]=React.useState<any[]>([]),[clients,setClients]=React.useState<any[]>([]),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(true);
 React.useEffect(()=>{let current=true;const load=async()=>{setLoading(true);setError('');const results=await Promise.allSettled([apiGet('/v1/identities'),includeClients?apiGet('/v1/clients'):Promise.resolve([])]);if(!current)return;
   setAgents(results[0].status==='fulfilled'?results[0].value.filter((x:any)=>x.identityType==='AI_AGENT'):[]);
   setClients(results[1].status==='fulfilled'?results[1].value.filter((x:any)=>x.status==='ACTIVE'&&(!x.workspaceId||x.workspaceId===WORKSPACE)):[]);
   setError(results.some(x=>x.status==='rejected')?'Some identity lists could not be loaded. Refresh or check API permissions.':'');setLoading(false);};void load();window.addEventListener('zt-refresh',load);return()=>{current=false;window.removeEventListener('zt-refresh',load)};},[includeClients]);
 const known=agents.some(x=>x.externalId===value)||clients.some(x=>'client:'+x.clientId===value);
 return <div><select value={value} disabled={disabled||loading} onChange={e=>onChange(e.target.value)} aria-label="Agent or caller"><option value="">{loading?'Loading identities…':'Select an agent / caller'}</option>{value&&!known&&<option value={value}>{value} (linked record)</option>}<optgroup label="Registered AI Agents">{agents.map(x=><option key={x.id} value={x.externalId}>{x.name||x.externalId} · {x.externalId}</option>)}</optgroup>{includeClients&&<optgroup label="Service Clients · authenticated subjects">{clients.map(x=><option key={x.id} value={'client:'+x.clientId}>{x.name} · client:{x.clientId}</option>)}</optgroup>}</select>{error&&<p role="alert">{error}</p>}{!loading&&!error&&!agents.length&&!clients.length&&<p>No registered identities. Register an identity or service client in Product Operations.</p>}</div>;
}

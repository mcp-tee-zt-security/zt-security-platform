import React from 'react';
import {apiGet,TENANT,DEFAULT_WORKSPACE,activateWorkspace,freezeWorkspace,hasPendingMutation} from '../api/client';
import {COMMON_OIDC,oidcSubject} from '../api/oidc';
type Workspace={id:string;name:string;slug:string;status:string};
type Scope={workspaces:Workspace[];selected:string;refresh:()=>Promise<void>;choose:(id:string)=>void};
const Context=React.createContext<Scope|null>(null);
export function useWorkspaceScope(){const value=React.useContext(Context);if(!value)throw Error('Workspace scope unavailable');return value;}
function preferenceKey(){return `zt-workspace:${TENANT}:${COMMON_OIDC?oidcSubject():'development'}`;}
export default function WorkspaceScope({children}:{children:React.ReactNode}){
 const [workspaces,setWorkspaces]=React.useState<Workspace[]>([]),[selected,setSelected]=React.useState(''),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(true);
 const alive=React.useRef(false),ticket=React.useRef(0);
 const refresh=React.useCallback(async()=>{const current=++ticket.current;try{
   const rows:Workspace[]=await apiGet('/v1/workspaces/accessible');if(!alive.current||current!==ticket.current)return;
   setWorkspaces(rows);if(!rows.length){freezeWorkspace();setSelected('');setError('No accessible workspaces. Ask an administrator to grant access.');return;}
   if(selected){if(!rows.some(w=>w.id===selected)){freezeWorkspace();setSelected('');setError('Workspace access was revoked. Choose another workspace below.');}return;}
   let saved='';try{saved=localStorage.getItem(preferenceKey())||''}catch{}
   const chosen=rows.find(w=>w.id===saved)?.id||rows.find(w=>w.id===DEFAULT_WORKSPACE)?.id||rows[0].id;
   activateWorkspace(chosen);setSelected(chosen);setError('');
 }catch(e:any){if(alive.current&&current===ticket.current){setError(e.message);if(!selected)setSelected('')}}finally{if(alive.current&&current===ticket.current)setLoading(false)}},[selected]);
 React.useEffect(()=>{alive.current=true;void refresh();return()=>{alive.current=false;ticket.current++}},[refresh]);
 React.useEffect(()=>{const reload=()=>void refresh();window.addEventListener('zt-workspaces-refresh',reload);window.addEventListener('zt-refresh',reload);return()=>{window.removeEventListener('zt-workspaces-refresh',reload);window.removeEventListener('zt-refresh',reload)}},[refresh]);
 const choose=(id:string)=>{if(id===selected)return;if(!workspaces.some(w=>w.id===id))return;if(hasPendingMutation()){setError('Wait for the current action to finish before switching workspaces.');return;}try{localStorage.setItem(preferenceKey(),id)}catch{setError('Browser storage is required to switch workspaces.');return;}freezeWorkspace();window.location.reload();};
 if(!selected)return <div className="panel"><h2>{loading?'Loading authorized workspaces…':'Workspace access'}</h2>{error&&<p role="alert">{error}</p>}{workspaces.length>0&&<select aria-label="Choose an accessible workspace" value="" onChange={e=>choose(e.target.value)}><option value="">Choose workspace</option>{workspaces.map(w=><option key={w.id} value={w.id}>{w.name}</option>)}</select>}{!loading&&<button onClick={()=>window.location.reload()}>Reload access</button>}</div>;
 return <Context.Provider value={{workspaces,selected,refresh,choose}}>{error&&<div className="error-banner" role="alert">{error}</div>}{children}</Context.Provider>;
}

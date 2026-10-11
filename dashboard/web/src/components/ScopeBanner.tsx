import React from 'react';
import {TENANT,apiGet,hasPendingMutation} from '../api/client';
import {useWorkspaceScope} from './WorkspaceScope';
import usePageRefresh from './usePageRefresh';
export default function ScopeBanner(){
 const scope=useWorkspaceScope();
 const [tenant,setTenant]=React.useState(''),[error,setError]=React.useState(''),[writing,setWriting]=React.useState(hasPendingMutation());
 React.useEffect(()=>{const changed=()=>setWriting(hasPendingMutation());window.addEventListener('zt-mutation-state',changed);return()=>window.removeEventListener('zt-mutation-state',changed)},[]);
 const load=React.useCallback(async()=>{try{const rows=await apiGet('/v1/tenants');setTenant(rows.find((x:any)=>x.id===TENANT)?.name||'');setError('')}catch{setError('Tenant name unavailable.')}},[]);
 usePageRefresh(load);React.useEffect(()=>{void load()},[load]);
 return <div className="workflow-context scope-selector"><div><strong>Tenant: {tenant||'Configured tenant'}</strong> <code>{TENANT}</code></div><label>Workspace<select value={scope.selected} disabled={writing} onChange={e=>scope.choose(e.target.value)}>{scope.workspaces.map(w=><option key={w.id} value={w.id}>{w.name}</option>)}</select></label><code>{scope.selected}</code><small>{error||'Server-authorized scope for all pages. Switching reloads the view and clears previous workspace results.'}{writing&&' Wait for the current action to finish.'} Tenant-wide inventories and policies remain explicitly shared.</small></div>;
}

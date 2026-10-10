import React from 'react';
import {TENANT,WORKSPACE,apiGet} from '../api/client';
import usePageRefresh from './usePageRefresh';
export default function ScopeBanner(){
 const [tenant,setTenant]=React.useState(''),[workspace,setWorkspace]=React.useState(''),[error,setError]=React.useState('');
 const load=React.useCallback(async()=>{const r=await Promise.allSettled([apiGet('/v1/tenants'),apiGet('/v1/workspaces')]);setTenant(r[0].status==='fulfilled'?r[0].value.find((x:any)=>x.id===TENANT)?.name||'':'');setWorkspace(r[1].status==='fulfilled'?r[1].value.find((x:any)=>x.id===WORKSPACE)?.name||'':'');setError(r.some(x=>x.status==='rejected')?'Scope names unavailable; requests still use the configured IDs.':'');},[]);
 usePageRefresh(load);React.useEffect(()=>{void load()},[load]);
 return <div className="workflow-context"><strong>Tenant: {tenant||'Configured tenant'}</strong> <code>{TENANT}</code> · <strong>Workspace: {workspace||'Configured workspace'}</strong> <code>{WORKSPACE}</code><small> Read-only scope · {error||'All requests use these IDs. Agents may be tenant-wide; service clients can be workspace-bound.'}</small></div>;
}

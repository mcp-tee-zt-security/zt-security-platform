import React from 'react';
import WorkspaceScope from './WorkspaceScope';
import {COMMON_OIDC,initializeOidc,loginOidc,logoutOidc,oidcUser} from '../api/oidc';

export default function AuthBoundary({children}:{children:React.ReactNode}){
 const [ready,setReady]=React.useState(!COMMON_OIDC),[user,setUser]=React.useState(''),[error,setError]=React.useState(''),[busy,setBusy]=React.useState(COMMON_OIDC);
 React.useEffect(()=>{if(!COMMON_OIDC)return;let alive=true;initializeOidc().then(ok=>{if(alive){setReady(ok);setUser(oidcUser());setBusy(false)}}).catch(e=>{if(alive){setError(e?.message||'Keycloak session could not be restored');setBusy(false)}});return()=>{alive=false}},[]);
 const login=async()=>{setBusy(true);setError('');try{await loginOidc()}catch(e:any){setError(e?.message||'Login failed');setBusy(false)}};
 if(!COMMON_OIDC)return <WorkspaceScope>{children}</WorkspaceScope>;
 if(!ready)return <div className="panel auth-login"><h1>ZT Security</h1><p>Sign in to request, approve and execute governed workflows.</p>{error&&<p role="alert">{error}</p>}<button disabled={busy} onClick={login}>{busy?'Restoring session…':'Sign in with Keycloak'}</button>{error&&<button onClick={()=>window.location.reload()}>Reload connection</button>}</div>;
 return <><div className="auth-session"><span>Signed in: <strong>{user}</strong></span><button onClick={()=>{setReady(false);setBusy(true);void logoutOidc().catch(e=>{setError(e?.message||'Logout failed');setBusy(false)})}}>Sign out / switch user</button></div><WorkspaceScope>{children}</WorkspaceScope></>;
}

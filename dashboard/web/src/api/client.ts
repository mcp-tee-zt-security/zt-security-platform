export const API = import.meta.env.VITE_API_URL || '/api';
export const TENANT = import.meta.env.VITE_TENANT_ID || '11111111-1111-1111-1111-111111111111';
export const API_KEY = import.meta.env.VITE_API_KEY || 'dev-master-key';
export const DEFAULT_WORKSPACE = import.meta.env.VITE_WORKSPACE_ID || '88888888-8888-8888-8888-888888888801';
export let WORKSPACE = DEFAULT_WORKSPACE;
let scopeGeneration=0,scopeChanging=false,scopeController=new AbortController(),mutations=0;
export function hasPendingMutation(){return mutations>0;}
export function activateWorkspace(id:string){
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))throw Error('Invalid workspace');
  scopeController.abort();scopeController=new AbortController();scopeGeneration++;WORKSPACE=id;scopeChanging=false;
}
export function freezeWorkspace(){scopeChanging=true;scopeGeneration++;scopeController.abort();}

const baseHeaders: Record<string,string> = {
  'X-API-Key': API_KEY,
  'X-Tenant-Id': TENANT,
  'Content-Type': 'application/json'
};

async function headers(extra: Record<string,string> = {}, useSession=true): Promise<Record<string,string>> {
  if(extra['X-Workspace-Id']&&extra['X-Workspace-Id']!==WORKSPACE)throw Error('Use the common workspace selector to change scope');
  const result:Record<string,string>={...baseHeaders, 'X-Workspace-Id': WORKSPACE, ...extra};
  const {COMMON_OIDC,oidcToken}=await import('./oidc');
  if(useSession&&COMMON_OIDC){delete result['X-API-Key'];result.Authorization=`Bearer ${await oidcToken()}`;}
  return result;
}
function ensureScope(generation:number){if(scopeChanging||generation!==scopeGeneration)throw new Error('Workspace changed; the previous request result was discarded');}
async function scopedFetch(path:string,method:string,body:unknown,buildHeaders:()=>Promise<Record<string,string>>){
  const generation=scopeGeneration,signal=scopeController.signal;ensureScope(generation);
  const requestHeaders=await buildHeaders();ensureScope(generation);
  const write=method!=='GET';if(write){mutations++;window.dispatchEvent(new Event('zt-mutation-state'));}
  try{
    const response=await fetch(`${API}${path}`,{method,headers:requestHeaders,signal,...(body===undefined?{}:{body:JSON.stringify(body)})});
    ensureScope(generation);const result=await parse(response,generation);ensureScope(generation);return result;
  }finally{if(write){mutations--;window.dispatchEvent(new Event('zt-mutation-state'));}}
}

async function parse(r: Response,generation:number) {
  const text = await r.text();
  let payload: any = null;
  try { payload = text ? JSON.parse(text) : null;
  } catch { payload = text;
  }
  ensureScope(generation);
  if (!r.ok) {
    const detail = typeof payload === 'string' ? payload : JSON.stringify(payload);
    throw new Error(`${r.status} ${r.statusText}${detail ? `: ${detail}` : ''}`);
  }
  window.dispatchEvent(new Event('zt-api-success'));
  return payload;
}

export async function apiGet(path: string, extra: Record<string,string> = {}) {
  return scopedFetch(path,'GET',undefined,()=>headers(extra));
}

export async function apiPost(path: string, body: unknown = {}, extra: Record<string,string> = {}) {
  return scopedFetch(path,'POST',body,()=>headers(extra));
}

export async function apiPut(path: string, body: unknown = {}) {
  return scopedFetch(path,'PUT',body,()=>headers());
}

export async function apiDelete(path: string) {
  return scopedFetch(path,'DELETE',undefined,()=>headers());
}

export type McpCredentials = {mode:'default'|'service'|'bearer'|'oidc'; clientId?:string; secret?:string};

// MCP workspace credentials are explicit and memory-only; other pages keep their configured authentication.
export async function mcpRequest(path:string, method='GET', body?:unknown, credentials:McpCredentials={mode:'default'}, extraHeaders:Record<string,string>={}) {
  return scopedFetch(path,method,body,async()=>{
  const requestHeaders=await headers({},credentials.mode==='default');
  for(const [key,value] of Object.entries(extraHeaders)){
    if(!['X-ZT-Delegation','X-ZT-Agent-Id','MCP-Protocol-Version','X-Workspace-Id'].includes(key))throw new Error('Unsupported protocol header');
    if(key==='X-Workspace-Id'&&!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value))throw new Error('A valid workspace ID is required');
    if(key==='X-Workspace-Id'&&value!==WORKSPACE)throw Error('Use the common workspace selector to change scope');
    requestHeaders[key]=value;
  }
  if(credentials.mode!=='default'){
    delete requestHeaders['X-API-Key'];
    if(credentials.mode==='oidc'){
      const {oidcToken}=await import('./oidc');
      requestHeaders.Authorization=`Bearer ${await oidcToken()}`;
    }
    else if(!credentials.secret?.trim())throw new Error('A credential is required');
    else if(credentials.mode==='bearer')requestHeaders.Authorization=`Bearer ${credentials.secret.trim()}`;
    else{
      if(!credentials.clientId?.trim())throw new Error('Service client ID is required');
      requestHeaders['X-Client-Id']=credentials.clientId.trim();
      requestHeaders['X-API-Key']=credentials.secret;
    }
  }
  return requestHeaders;
  });
}

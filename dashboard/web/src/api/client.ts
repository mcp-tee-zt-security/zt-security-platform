export const API = import.meta.env.VITE_API_URL || '/api';
export const TENANT = import.meta.env.VITE_TENANT_ID || '11111111-1111-1111-1111-111111111111';
export const API_KEY = import.meta.env.VITE_API_KEY || 'dev-master-key';
export const WORKSPACE = import.meta.env.VITE_WORKSPACE_ID || '88888888-8888-8888-8888-888888888801';

const baseHeaders: Record<string,string> = {
  'X-API-Key': API_KEY,
  'X-Tenant-Id': TENANT,
  'Content-Type': 'application/json'
};

function headers(extra: Record<string,string> = {}): Record<string,string> {
  return {...baseHeaders, 'X-Workspace-Id': WORKSPACE, ...extra};
}

async function parse(r: Response) {
  const text = await r.text();
  let payload: any = null;
  try { payload = text ? JSON.parse(text) : null;
  } catch { payload = text;
  }
  if (!r.ok) {
    const detail = typeof payload === 'string' ? payload : JSON.stringify(payload);
    throw new Error(`${r.status} ${r.statusText}${detail ? `: ${detail}` : ''}`);
  }
  return payload;
}

export async function apiGet(path: string) {
  return parse(await fetch(`${API}${path}`, {headers: headers()}));
}

export async function apiPost(path: string, body: unknown = {}, extra: Record<string,string> = {}) {
  return parse(await fetch(`${API}${path}`, {
    method: 'POST',
    headers: headers(extra),
    body: JSON.stringify(body)
  }));
}

export async function apiPut(path: string, body: unknown = {}) {
  return parse(await fetch(`${API}${path}`, {
    method: 'PUT',
    headers: headers(),
    body: JSON.stringify(body)
  }));
}

export async function apiDelete(path: string) {
  return parse(await fetch(`${API}${path}`, {method: 'DELETE', headers: headers()}));
}

export type McpCredentials = {mode:'default'|'service'|'bearer'; clientId?:string; secret?:string};

// MCP workspace credentials are explicit and memory-only; other pages keep their configured authentication.
export async function mcpRequest(path:string, method='GET', body?:unknown, credentials:McpCredentials={mode:'default'}) {
  const requestHeaders=headers();
  if(credentials.mode!=='default'){
    delete requestHeaders['X-API-Key'];
    if(!credentials.secret?.trim())throw new Error('A credential is required');
    if(credentials.mode==='bearer')requestHeaders.Authorization=`Bearer ${credentials.secret.trim()}`;
    else{
      if(!credentials.clientId?.trim())throw new Error('Service client ID is required');
      requestHeaders['X-Client-Id']=credentials.clientId.trim();
      requestHeaders['X-API-Key']=credentials.secret;
    }
  }
  return parse(await fetch(`${API}${path}`,{method,headers:requestHeaders,
    ...(body===undefined?{}:{body:JSON.stringify(body)})}));
}
